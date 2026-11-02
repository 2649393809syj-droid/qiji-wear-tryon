package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runs isolated baseline/candidate comparisons without updating wardrobe business rows. */
@Service
public class WardrobePromptExperimentService {

    private static final Logger log = LoggerFactory.getLogger(WardrobePromptExperimentService.class);
    private static final String TABLE = "qj_wardrobe_prompt_experiment_result";
    private static final List<String> STYLES = Arrays.asList(
            "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女");

    private final Store store;
    private final WardrobePromptVersionService promptService;
    private final GeminiStyleRecognitionService gemini;
    private final WardrobeEvaluationNormalizer normalizer;
    private final TaskExecutor executor;
    private final Set<Long> scheduledOptimizations = ConcurrentHashMap.newKeySet();

    @Autowired
    public WardrobePromptExperimentService(Store store,
                                           WardrobePromptVersionService promptService,
                                           GeminiStyleRecognitionService gemini,
                                           WardrobeEvaluationNormalizer normalizer,
                                           @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.store = store;
        this.promptService = promptService;
        this.gemini = gemini;
        this.normalizer = normalizer;
        this.executor = executor;
    }

    public JSONObject start(Long candidateId, Long optimizationId) throws SQLException {
        Entity optimization = store.findOptimization(optimizationId);
        validateOptimization(optimization, candidateId);
        Long resolvedOptimizationId = optimization.getLong("id");
        WardrobePromptVersionService.PromptVersion baseline = promptService.get(
                optimization.getLong("base_prompt_version_id"));
        WardrobePromptVersionService.PromptVersion candidate = promptService.get(candidateId);
        if (!"candidate".equals(candidate.getStatus()) && !"evaluating".equals(candidate.getStatus())) {
            throw new IllegalStateException("Only a candidate or evaluating Prompt can be evaluated offline");
        }

        List<Entity> existing = store.listResults(resolvedOptimizationId);
        if (!existing.isEmpty()) {
            JSONObject summary = summarize(existing);
            if ("running".equals(summary.getStr("status"))) {
                schedule(resolvedOptimizationId, candidateId, baseline.getPromptText(),
                        candidate.getPromptText(), toRunItems(store.findRunnableResults(resolvedOptimizationId)));
            }
            return summarize(store.listResults(resolvedOptimizationId));
        }

        List<Long> trainingIds = parseSnapshotIds(optimization.getStr("sample_ids_json"), "training");
        List<Long> heldoutIds = parseSnapshotIds(optimization.getStr("heldout_sample_ids_json"), "held-out");
        Set<Long> training = new HashSet<>(trainingIds);
        for (Long heldoutId : heldoutIds) {
            if (training.contains(heldoutId)) {
                throw new IllegalArgumentException("Training and held-out snapshots must be disjoint");
            }
        }
        List<Entity> selected = snapshotRows(heldoutIds);

        List<Entity> pendingResults = new ArrayList<>();
        for (Entity sample : selected) {
            JSONObject advisor = JSONUtil.parseObj(sample.getStr("advisor_evaluation_json"));
            pendingResults.add(Entity.create(TABLE)
                    .set("optimization_id", resolvedOptimizationId)
                    .set("candidate_prompt_version_id", candidateId)
                    .set("baseline_prompt_version_id", baseline.getId())
                    .set("wardrobe_item_id", sample.getLong("id"))
                    .set("status", "pending")
                    .set("advisor_evaluation_json", advisor.toString())
                    .set("create_time", new Date())
                    .set("_run_image_url", sample.getStr("image_url")));
        }
        List<RunItem> runItems = store.insertResults(pendingResults);
        schedule(resolvedOptimizationId, candidateId,
                baseline.getPromptText(), candidate.getPromptText(), runItems);
        return summarize(store.listResults(resolvedOptimizationId));
    }

    public JSONObject get(Long candidateId, Long optimizationId) throws SQLException {
        Entity optimization = store.findOptimization(optimizationId);
        validateOptimization(optimization, candidateId);
        return summarize(store.listResults(optimization.getLong("id")));
    }

    private void run(Long optimizationId, Long candidateId, String baselinePrompt,
                     String candidatePrompt, List<RunItem> items) {
        for (RunItem item : items) {
            try {
                store.markRunning(item.resultId);
                JSONObject baseline = gemini.recognize(item.imageUrl, baselinePrompt).getEvaluationJson();
                JSONObject candidate = gemini.recognize(item.imageUrl, candidatePrompt).getEvaluationJson();
                WardrobeEvaluationNormalizer.Comparison baselineComparison =
                        normalizer.compare(baseline, item.advisor);
                WardrobeEvaluationNormalizer.Comparison candidateComparison =
                        normalizer.compare(candidate, item.advisor);
                store.markCompleted(item.resultId,
                        baselineComparison.getAiEvaluation(), candidateComparison.getAiEvaluation(),
                        baselineComparison.getDiffJson(), candidateComparison.getDiffJson());
            } catch (Exception e) {
                try {
                    store.markFailed(item.resultId, safeMessage(e));
                } catch (SQLException persistenceError) {
                    log.error("Failed to persist wardrobe Prompt experiment error", persistenceError);
                }
            }
        }
    }

    private void schedule(Long optimizationId, Long candidateId, String baselinePrompt,
                          String candidatePrompt, List<RunItem> items) {
        if (items.isEmpty() || !scheduledOptimizations.add(optimizationId)) return;
        try {
            executor.execute(() -> {
                try {
                    run(optimizationId, candidateId, baselinePrompt, candidatePrompt, items);
                } finally {
                    scheduledOptimizations.remove(optimizationId);
                }
            });
        } catch (RuntimeException error) {
            scheduledOptimizations.remove(optimizationId);
            throw error;
        }
    }

    private List<RunItem> toRunItems(List<Entity> rows) {
        List<RunItem> items = new ArrayList<>();
        for (Entity row : rows) {
            JSONObject advisor = parseObj(row.getStr("advisor_evaluation_json"));
            if (row.getLong("id") != null && StrUtil.isNotBlank(row.getStr("image_url")) && advisor != null) {
                items.add(new RunItem(row.getLong("id"), row.getStr("image_url"), advisor));
            }
        }
        return items;
    }

    private void validateOptimization(Entity optimization, Long candidateId) {
        if (candidateId == null) {
            throw new IllegalArgumentException("candidateId is required");
        }
        if (optimization == null) {
            throw new IllegalArgumentException("Prompt optimization job does not exist");
        }
        if (!"completed".equals(optimization.getStr("status"))
                && !"evaluating".equals(optimization.getStr("status"))) {
            throw new IllegalStateException("Prompt optimization job is not ready for offline evaluation");
        }
        if (!candidateId.equals(optimization.getLong("candidate_prompt_version_id"))) {
            throw new IllegalArgumentException("Candidate does not belong to this optimization job");
        }
    }

    private List<Long> parseSnapshotIds(String raw, String snapshotName) {
        JSONArray ids;
        try {
            ids = parseArray(raw);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(snapshotName + " sample snapshot is invalid", error);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(snapshotName + " sample snapshot must be non-empty");
        }
        List<Long> result = ids.toList(Long.class);
        if (result.contains(null) || result.size() != new HashSet<>(result).size()) {
            throw new IllegalArgumentException(snapshotName + " sample snapshot must contain unique IDs");
        }
        return result;
    }

    private List<Entity> snapshotRows(List<Long> ids) throws SQLException {
        List<Entity> rows = store.findSamplesByIds(ids);
        Map<Long, Entity> byId = new LinkedHashMap<>();
        if (rows != null) {
            for (Entity row : rows) {
                if (row != null && row.getLong("id") != null) byId.put(row.getLong("id"), row);
            }
        }
        List<Entity> ordered = new ArrayList<>();
        for (Long id : ids) {
            Entity row = byId.get(id);
            if (row == null) {
                throw new IllegalArgumentException("Held-out reviewed sample is missing: " + id);
            }
            validateSnapshot(row, id);
            ordered.add(copy(row));
        }
        return ordered;
    }

    private void validateSnapshot(Entity row, Long id) {
        if (StrUtil.isBlank(row.getStr("image_url"))) {
            throw new IllegalArgumentException("Held-out reviewed sample has no image: " + id);
        }
        JSONObject ai = parseObj(row.getStr("ai_evaluation_json"));
        JSONObject advisor = parseObj(row.getStr("advisor_evaluation_json"));
        if (ai == null || advisor == null) {
            throw new IllegalArgumentException("Held-out reviewed sample requires AI and advisor snapshots: " + id);
        }
        normalizer.validateAlignedSnapshot(ai);
        normalizer.validateAlignedSnapshot(advisor);
    }

    private JSONObject summarize(List<Entity> rows) {
        int pending = 0;
        int running = 0;
        int completed = 0;
        int failed = 0;
        JSONArray errors = new JSONArray();
        List<Entity> completedRows = new ArrayList<>();
        for (Entity row : rows) {
            String status = row.getStr("status");
            if ("pending".equals(status)) pending++;
            else if ("running".equals(status)) running++;
            else if ("completed".equals(status)) {
                completed++;
                completedRows.add(row);
            } else if ("failed".equals(status)) {
                failed++;
                errors.add(new JSONObject().set("wardrobeItemId", row.getLong("wardrobe_item_id"))
                        .set("errorMsg", row.getStr("error_msg")));
            }
        }
        String status = pending + running > 0 ? "running" : (completed > 0 ? "completed" : "failed");
        JSONObject baselineMetrics = metrics(completedRows, "baseline_evaluation_json", "baseline_diff_json");
        JSONObject candidateMetrics = metrics(completedRows, "candidate_evaluation_json", "candidate_diff_json");
        return new JSONObject().set("status", status).set("total", rows.size())
                .set("pending", pending).set("running", running)
                .set("completed", completed).set("failed", failed)
                .set("baselineMetrics", baselineMetrics).set("candidateMetrics", candidateMetrics)
                .set("errors", errors);
    }

    private JSONObject metrics(List<Entity> rows, String evaluationColumn, String diffColumn) {
        int exactMatches = 0;
        double absoluteError = 0;
        int styleValues = 0;
        for (Entity row : rows) {
            JSONObject diff = parseObj(row.getStr(diffColumn));
            if (diff != null && diff.isEmpty()) exactMatches++;
            JSONObject evaluation = parseObj(row.getStr(evaluationColumn));
            JSONObject advisor = parseObj(row.getStr("advisor_evaluation_json"));
            if (evaluation == null || advisor == null) continue;
            Map<String, Integer> predicted = styleScores(evaluation.getJSONArray("types"));
            Map<String, Integer> expected = styleScores(advisor.getJSONArray("types"));
            for (String style : STYLES) {
                absoluteError += Math.abs(predicted.get(style) - expected.get(style));
                styleValues++;
            }
        }
        double mae = styleValues == 0 ? 0 : absoluteError / styleValues;
        double exactRate = rows.isEmpty() ? 0 : exactMatches * 1.0 / rows.size();
        return new JSONObject().set("sampleCount", rows.size())
                .set("exactMatches", exactMatches).set("exactMatchRate", exactRate)
                .set("styleMae", Math.round(mae * 100.0) / 100.0);
    }

    private Map<String, Integer> styleScores(JSONArray values) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        STYLES.forEach(style -> scores.put(style, 0));
        if (values == null) return scores;
        for (Object value : values) {
            JSONObject item = value instanceof JSONObject ? (JSONObject) value : new JSONObject(value);
            String style = item.getStr("type");
            String raw = item.getStr("typeProportion");
            if (scores.containsKey(style) && raw != null && raw.matches("\\d+%")) {
                scores.put(style, Integer.parseInt(raw.substring(0, raw.length() - 1)));
            }
        }
        return scores;
    }

    private JSONObject parseObj(String value) {
        return StrUtil.isBlank(value) ? null : JSONUtil.parseObj(value);
    }

    private JSONArray parseArray(String value) {
        return StrUtil.isBlank(value) ? new JSONArray() : JSONUtil.parseArray(value);
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (StrUtil.isBlank(message)) return "Offline Prompt evaluation failed";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private Entity copy(Entity source) {
        Entity target = Entity.create(source.getTableName());
        target.putAll(source);
        return target;
    }

    static final class RunItem {
        private final Long resultId;
        private final String imageUrl;
        private final JSONObject advisor;

        RunItem(Long resultId, String imageUrl, JSONObject advisor) {
            this.resultId = resultId;
            this.imageUrl = imageUrl;
            this.advisor = JSONUtil.parseObj(advisor.toString());
        }
    }

    public interface Store {
        Entity findOptimization(Long optimizationId) throws SQLException;
        Entity findOptimizationForCandidate(Long candidateId) throws SQLException;
        List<Entity> findSamplesByIds(List<Long> ids) throws SQLException;
        List<RunItem> insertResults(List<Entity> rows) throws SQLException;
        List<Entity> findRunnableResults(Long optimizationId) throws SQLException;
        void markRunning(Long resultId) throws SQLException;
        void markCompleted(Long resultId, JSONObject baseline, JSONObject candidate,
                           JSONObject baselineDiff, JSONObject candidateDiff) throws SQLException;
        void markFailed(Long resultId, String error) throws SQLException;
        List<Entity> listResults(Long optimizationId) throws SQLException;
    }

    @Service
    public static final class HutoolStore implements Store {
        private Db db() {
            return DBConfig.getDb(null, "qj");
        }

        @Override
        public Entity findOptimization(Long optimizationId) throws SQLException {
            return optimizationId == null ? null : db().queryOne(
                    "SELECT * FROM qj_wardrobe_prompt_optimization WHERE id = ?", optimizationId);
        }

        @Override
        public Entity findOptimizationForCandidate(Long candidateId) throws SQLException {
            return candidateId == null ? null : db().queryOne(
                    "SELECT * FROM qj_wardrobe_prompt_optimization"
                            + " WHERE candidate_prompt_version_id = ? ORDER BY id DESC LIMIT 1", candidateId);
        }

        @Override
        public List<Entity> findSamplesByIds(List<Long> ids) throws SQLException {
            if (ids == null || ids.isEmpty()) return new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT id, image_url, ai_evaluation_json, advisor_evaluation_json"
                    + " FROM qj_wardrobe_item WHERE flow_version = 'single_pass_v2'"
                    + " AND status = 'completed'"
                    + " AND sample_type IN ('positive','negative')"
                    + " AND id IN (");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
                params.add(ids.get(i));
            }
            sql.append(')');
            return new ArrayList<>(db().query(sql.toString(), params.toArray()));
        }

        @Override
        public List<RunItem> insertResults(List<Entity> rows) throws SQLException {
            Session session = newSession();
            List<RunItem> items = new ArrayList<>();
            try {
                session.beginTransaction();
                for (Entity source : rows) {
                    Entity row = copyForInsert(source);
                    String imageUrl = source.getStr("_run_image_url");
                    Long id = session.insertForGeneratedKey(row);
                    items.add(new RunItem(id, imageUrl,
                            JSONUtil.parseObj(row.getStr("advisor_evaluation_json"))));
                }
                session.commit();
                return items;
            } catch (SQLException | RuntimeException error) {
                session.rollback();
                throw error;
            } finally {
                session.close();
            }
        }

        @Override
        public List<Entity> findRunnableResults(Long optimizationId) throws SQLException {
            return new ArrayList<>(db().query("SELECT r.id, r.advisor_evaluation_json, i.image_url"
                    + " FROM " + TABLE + " r JOIN qj_wardrobe_item i ON i.id = r.wardrobe_item_id"
                    + " WHERE r.optimization_id = ? AND r.status IN ('pending','running') ORDER BY r.id",
                    optimizationId));
        }

        @Override
        public void markRunning(Long resultId) throws SQLException {
            db().execute("UPDATE " + TABLE + " SET status = 'running'"
                    + " WHERE id = ? AND status = 'pending'", resultId);
        }

        @Override
        public void markCompleted(Long resultId, JSONObject baseline, JSONObject candidate,
                                  JSONObject baselineDiff, JSONObject candidateDiff) throws SQLException {
            db().execute("UPDATE " + TABLE + " SET status = 'completed', baseline_evaluation_json = ?,"
                            + " candidate_evaluation_json = ?, baseline_diff_json = ?, candidate_diff_json = ?,"
                            + " error_msg = NULL, finish_time = NOW() WHERE id = ?",
                    baseline.toString(), candidate.toString(), baselineDiff.toString(),
                    candidateDiff.toString(), resultId);
        }

        @Override
        public void markFailed(Long resultId, String error) throws SQLException {
            db().execute("UPDATE " + TABLE + " SET status = 'failed', error_msg = ?, finish_time = NOW()"
                    + " WHERE id = ?", error, resultId);
        }

        @Override
        public List<Entity> listResults(Long optimizationId) throws SQLException {
            return new ArrayList<>(db().query("SELECT * FROM " + TABLE
                    + " WHERE optimization_id = ? ORDER BY id", optimizationId));
        }

        private Session newSession() {
            String key = DBConfig.getDbSetting().getStr("db_env") + "_qj";
            return Session.create(DSFactory.get(key));
        }

        private Entity copyForInsert(Entity source) {
            Entity target = Entity.create(TABLE);
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (!entry.getKey().startsWith("_")) target.set(entry.getKey(), entry.getValue());
            }
            return target;
        }
    }
}
