package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes deterministic global quality metrics from immutable wardrobe review snapshots. */
@Service
public class WardrobeAiStatisticsService {

    private static final List<String> STYLES = Arrays.asList(
            "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女");
    private final Store store;

    public WardrobeAiStatisticsService(Store store) {
        this.store = store;
    }

    public JSONObject get(JSONObject filter) throws SQLException {
        JSONObject safeFilter = filter == null ? new JSONObject() : JSONUtil.parseObj(filter.toString());
        validateFilter(safeFilter);
        List<Entity> statusRows = store.findStatusCounts(safeFilter);
        List<Entity> reviewedRows = store.findReviewed(safeFilter);

        Category total = new Category(null);
        Map<Integer, Category> categories = new LinkedHashMap<>();
        categories.put(1, new Category(1));
        categories.put(2, new Category(2));
        for (Entity row : statusRows) {
            Integer navValue = row.getInt("nav_item_cur");
            Long countValue = row.getLong("total");
            int nav = navValue == null ? 0 : navValue;
            long count = countValue == null ? 0 : countValue;
            String status = row.getStr("status");
            total.addStatus(status, count);
            if (nav == 1 || nav == 2) {
                categories.computeIfAbsent(nav, Category::new).addStatus(status, count);
            }
        }

        MetricAccumulator globalMetrics = new MetricAccumulator();
        Map<Integer, MetricAccumulator> categoryMetrics = new LinkedHashMap<>();
        Map<String, Integer> fieldCorrections = new LinkedHashMap<>();
        long fieldCorrectedReviews = 0;
        for (Entity row : reviewedRows) {
            Integer navValue = row.getInt("nav_item_cur");
            int nav = navValue == null ? 0 : navValue;
            Category category = nav == 1 || nav == 2
                    ? categories.computeIfAbsent(nav, Category::new) : null;
            MetricAccumulator navMetrics = categoryMetrics.computeIfAbsent(nav, ignored -> new MetricAccumulator());
            String sampleType = row.getStr("sample_type");
            total.addReview(sampleType);
            if (category != null) category.addReview(sampleType);

            JSONObject ai = parseObj(row.getStr("ai_evaluation_json"));
            JSONObject advisor = parseObj(row.getStr("advisor_evaluation_json"));
            globalMetrics.add(ai, advisor);
            if (category != null) navMetrics.add(ai, advisor);

            JSONObject diff = parseObj(row.getStr("diff_json"));
            boolean fieldCorrected = false;
            if (diff != null) {
                for (String field : diff.keySet()) {
                    if (!field.startsWith("types.")) {
                        fieldCorrections.put(field, fieldCorrections.getOrDefault(field, 0) + 1);
                        fieldCorrected = true;
                    }
                }
            }
            if (fieldCorrected) fieldCorrectedReviews++;
        }

        JSONArray categoryJson = new JSONArray();
        for (Category category : categories.values()) {
            if (category.total == 0 && category.reviewed == 0) continue;
            MetricAccumulator metrics = categoryMetrics.getOrDefault(category.navItemCur, new MetricAccumulator());
            categoryJson.add(category.toJson().set("styleMae", metrics.mae()));
        }

        long terminal = total.aiSuccess + total.aiFailed;
        JSONObject fieldRates = new JSONObject();
        for (Map.Entry<String, Integer> entry : fieldCorrections.entrySet()) {
            fieldRates.set(entry.getKey(), rate(entry.getValue(), total.reviewed));
        }
        return new JSONObject()
                .set("filter", safeFilter)
                .set("uploads", new JSONObject().set("total", total.total)
                        .set("aiSuccess", total.aiSuccess).set("aiFailed", total.aiFailed)
                        .set("pendingAi", total.pendingAi)
                        .set("successRate", rate(total.aiSuccess, terminal))
                        .set("failureRate", rate(total.aiFailed, terminal)))
                .set("reviews", new JSONObject().set("reviewed", total.reviewed)
                        .set("positive", total.positive).set("negative", total.negative)
                        .set("positiveRate", rate(total.positive, total.reviewed)))
                .set("styles", new JSONObject().set("mae", globalMetrics.mae())
                        .set("omissionCount", globalMetrics.omissionCount)
                        .set("falsePositiveCount", globalMetrics.falsePositiveCount)
                        .set("omissionRate", rate(globalMetrics.omissionCount, globalMetrics.advisorNonZero))
                        .set("falsePositiveRate", rate(globalMetrics.falsePositiveCount, globalMetrics.aiNonZero)))
                .set("fields", new JSONObject()
                        .set("correctionCount", fieldCorrections.values().stream().mapToInt(Integer::intValue).sum())
                        .set("correctionRate", rate(fieldCorrectedReviews, total.reviewed))
                        .set("byField", fieldCorrections).set("rateByField", fieldRates))
                .set("categories", categoryJson);
    }

    private void validateFilter(JSONObject filter) {
        Integer nav = filter.getInt("navItemCur");
        if (nav != null && nav != 1 && nav != 2) {
            throw new IllegalArgumentException("navItemCur must be 1 or 2");
        }
    }

    private JSONObject parseObj(String value) {
        return StrUtil.isBlank(value) ? null : JSONUtil.parseObj(value);
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    private static final class Category {
        private final Integer navItemCur;
        private long total;
        private long aiSuccess;
        private long aiFailed;
        private long pendingAi;
        private long reviewed;
        private long positive;
        private long negative;

        private Category(Integer navItemCur) {
            this.navItemCur = navItemCur;
        }

        private void addStatus(String status, long count) {
            total += count;
            if ("pending_review".equals(status) || "completed".equals(status)) aiSuccess += count;
            else if ("ai_failed".equals(status)) aiFailed += count;
            else if ("pending_ai".equals(status)) pendingAi += count;
        }

        private void addReview(String sampleType) {
            if ("positive".equals(sampleType)) {
                reviewed++;
                positive++;
            } else if ("negative".equals(sampleType)) {
                reviewed++;
                negative++;
            }
        }

        private JSONObject toJson() {
            long terminal = aiSuccess + aiFailed;
            return new JSONObject().set("navItemCur", navItemCur)
                    .set("name", navItemCur != null && navItemCur == 2 ? "饰品" : "服装")
                    .set("total", total).set("aiSuccess", aiSuccess).set("aiFailed", aiFailed)
                    .set("successRate", terminal == 0 ? 0 : Math.round(aiSuccess * 10000.0 / terminal) / 10000.0)
                    .set("reviewed", reviewed).set("positive", positive).set("negative", negative)
                    .set("positiveRate", reviewed == 0 ? 0 : Math.round(positive * 10000.0 / reviewed) / 10000.0);
        }
    }

    private static final class MetricAccumulator {
        private double absoluteError;
        private long styleValues;
        private long omissionCount;
        private long falsePositiveCount;
        private long aiNonZero;
        private long advisorNonZero;

        private void add(JSONObject ai, JSONObject advisor) {
            if (ai == null || advisor == null) return;
            Map<String, Integer> aiScores = scores(ai.getJSONArray("types"));
            Map<String, Integer> advisorScores = scores(advisor.getJSONArray("types"));
            for (String style : STYLES) {
                int predicted = aiScores.get(style);
                int expected = advisorScores.get(style);
                absoluteError += Math.abs(predicted - expected);
                styleValues++;
                if (predicted > 0) aiNonZero++;
                if (expected > 0) advisorNonZero++;
                if (predicted == 0 && expected > 0) omissionCount++;
                if (predicted > 0 && expected == 0) falsePositiveCount++;
            }
        }

        private double mae() {
            if (styleValues == 0) return 0;
            return Math.round(absoluteError * 100.0 / styleValues) / 100.0;
        }

        private Map<String, Integer> scores(JSONArray values) {
            Map<String, Integer> result = new LinkedHashMap<>();
            STYLES.forEach(style -> result.put(style, 0));
            if (values == null) return result;
            for (Object raw : values) {
                JSONObject value = raw instanceof JSONObject ? (JSONObject) raw : new JSONObject(raw);
                String style = value.getStr("type");
                String percentage = value.getStr("typeProportion");
                if (result.containsKey(style) && percentage != null && percentage.matches("\\d+%")) {
                    result.put(style, Integer.parseInt(percentage.substring(0, percentage.length() - 1)));
                }
            }
            return result;
        }
    }

    public interface Store {
        List<Entity> findStatusCounts(JSONObject filter) throws SQLException;
        List<Entity> findReviewed(JSONObject filter) throws SQLException;
    }

    @Service
    public static final class HutoolStore implements Store {
        private Db db() {
            return DBConfig.getDb(null, "qj");
        }

        @Override
        public List<Entity> findStatusCounts(JSONObject filter) throws SQLException {
            Query query = filtered("SELECT nav_item_cur, status, COUNT(*) AS total"
                    + " FROM qj_wardrobe_item WHERE flow_version = 'single_pass_v2'", filter);
            query.sql.append(" GROUP BY nav_item_cur, status ORDER BY nav_item_cur, status");
            return new ArrayList<>(db().query(query.sql.toString(), query.params.toArray()));
        }

        @Override
        public List<Entity> findReviewed(JSONObject filter) throws SQLException {
            Query query = filtered("SELECT nav_item_cur, sample_type, ai_evaluation_json,"
                    + " advisor_evaluation_json, diff_json FROM qj_wardrobe_item"
                    + " WHERE flow_version = 'single_pass_v2'", filter);
            query.sql.append(" AND status = 'completed' AND sample_type IN ('positive','negative')"
                    + " AND ai_evaluation_json IS NOT NULL AND advisor_evaluation_json IS NOT NULL"
                    + " ORDER BY id");
            return new ArrayList<>(db().query(query.sql.toString(), query.params.toArray()));
        }

        private Query filtered(String base, JSONObject filter) {
            Query query = new Query(base);
            Integer nav = filter.getInt("navItemCur");
            if (nav != null) {
                query.sql.append(" AND nav_item_cur = ?");
                query.params.add(nav);
            }
            Long promptVersionId = filter.getLong("promptVersionId");
            if (promptVersionId != null) {
                query.sql.append(" AND prompt_version_id = ?");
                query.params.add(promptVersionId);
            }
            Date from = filter.getDate("from");
            if (from != null) {
                query.sql.append(" AND create_time >= ?");
                query.params.add(from);
            }
            Date to = filter.getDate("to");
            if (to != null) {
                query.sql.append(" AND create_time < ?");
                query.params.add(to);
            }
            return query;
        }

        private static final class Query {
            private final StringBuilder sql;
            private final List<Object> params = new ArrayList<>();

            private Query(String sql) {
                this.sql = new StringBuilder(sql);
            }
        }
    }
}
