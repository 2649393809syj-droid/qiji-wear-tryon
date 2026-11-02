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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WardrobePromptReleaseMonitorService {
    private static final Logger log = LoggerFactory.getLogger(WardrobePromptReleaseMonitorService.class);
    private static final String MONITOR_TABLE = "qj_wardrobe_prompt_release_monitor";
    private static final String RESULT_TABLE = "qj_wardrobe_prompt_release_result";
    private static final List<String> STYLES = List.of(
            "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女");

    private final Store store;
    private final WardrobePromptVersionService prompts;
    private final GeminiStyleRecognitionService gemini;
    private final WardrobeEvaluationNormalizer normalizer;
    private final WardrobePromptRollbackPolicy policy;
    private final TaskExecutor executor;
    private final boolean enabled;
    private final int minimumSamples;
    private final double maximumFailureRate;
    private final double minimumExactRegression;
    private final double minimumStyleMaeRegression;
    private final int shadowRetries;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    @Autowired
    public WardrobePromptReleaseMonitorService(
            Store store, WardrobePromptVersionService prompts, GeminiStyleRecognitionService gemini,
            WardrobeEvaluationNormalizer normalizer,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Value("${wardrobe.prompt.monitor.enabled:true}") boolean enabled,
            @Value("${wardrobe.prompt.monitor.minimum-samples:4}") int minimumSamples,
            @Value("${wardrobe.prompt.monitor.maximum-failure-rate:0.20}") double maximumFailureRate,
            @Value("${wardrobe.prompt.monitor.minimum-exact-regression:0.25}") double minimumExactRegression,
            @Value("${wardrobe.prompt.monitor.minimum-style-mae-regression:5.0}") double minimumStyleMaeRegression,
            @Value("${wardrobe.prompt.monitor.shadow-retries:2}") int shadowRetries) {
        this(store, prompts, gemini, normalizer, new WardrobePromptRollbackPolicy(), executor,
                enabled, minimumSamples, maximumFailureRate, minimumExactRegression,
                minimumStyleMaeRegression, shadowRetries);
    }

    WardrobePromptReleaseMonitorService(
            Store store, WardrobePromptVersionService prompts, GeminiStyleRecognitionService gemini,
            WardrobeEvaluationNormalizer normalizer, WardrobePromptRollbackPolicy policy,
            TaskExecutor executor, boolean enabled, int minimumSamples, double maximumFailureRate,
            double minimumExactRegression, double minimumStyleMaeRegression, int shadowRetries) {
        if (minimumSamples <= 0 || shadowRetries < 0) {
            throw new IllegalArgumentException("Prompt release monitor thresholds are invalid");
        }
        this.store = store;
        this.prompts = prompts;
        this.gemini = gemini;
        this.normalizer = normalizer;
        this.policy = policy;
        this.executor = executor;
        this.enabled = enabled;
        this.minimumSamples = minimumSamples;
        this.maximumFailureRate = maximumFailureRate;
        this.minimumExactRegression = minimumExactRegression;
        this.minimumStyleMaeRegression = minimumStyleMaeRegression;
        this.shadowRetries = shadowRetries;
    }

    @EventListener
    public void onReviewCompleted(WardrobeReviewCompletedEvent event) {
        requestRun();
    }

    @Scheduled(fixedDelayString = "${wardrobe.prompt.monitor.recovery-delay-ms:60000}")
    public void recover() {
        requestRun();
    }

    public void requestRun() {
        if (!enabled || !scheduled.compareAndSet(false, true)) return;
        try {
            executor.execute(this::runUntilIdle);
        } catch (RuntimeException error) {
            scheduled.set(false);
            log.warn("Unable to schedule Prompt release monitor", error);
        }
    }

    public boolean blocksNewOptimization() throws SQLException {
        return enabled && store.blocksLatestAutomaticPublication();
    }

    public JSONObject advanceOnce() throws Exception {
        if (!enabled) return action("idle", null);
        Entity monitor = ensureMonitor();
        if (monitor == null) return action("idle", null);
        String status = monitor.getStr("status");
        if (!"collecting".equals(status) && !"evaluating".equals(status)) {
            return action(status, monitor);
        }

        WardrobePromptVersionService.PromptVersion active = prompts.getActivePrompt();
        if (!monitor.getLong("published_prompt_version_id").equals(active.getId())) {
            store.markStale(monitor.getLong("id"), "active_prompt_changed");
            return action("stale", store.findMonitor(monitor.getLong("id")));
        }
        if ("collecting".equals(status)) return collect(monitor);
        return evaluate(monitor);
    }

    private Entity ensureMonitor() throws SQLException {
        Entity publication = store.latestAutomaticPublication();
        if (publication == null) return null;
        Entity existing = store.findMonitorByPublication(publication.getLong("id"));
        if (existing != null) return existing;
        Entity active = store.findActiveMonitor();
        if (active != null) return active;
        long floor = longValue(publication.getLong("publication_floor"));
        JSONObject thresholds = new JSONObject().set("minimumSamples", minimumSamples)
                .set("maximumFailureRate", maximumFailureRate)
                .set("minimumExactRegression", minimumExactRegression)
                .set("minimumStyleMaeRegression", minimumStyleMaeRegression)
                .set("shadowRetries", shadowRetries)
                .set("publicationFloor", floor);
        Long id = store.insertMonitor(Entity.create(MONITOR_TABLE)
                .set("publication_audit_id", publication.getLong("id"))
                .set("optimization_id", publication.getLong("optimization_id"))
                .set("published_prompt_version_id", publication.getLong("to_version_id"))
                .set("baseline_prompt_version_id", publication.getLong("from_version_id"))
                .set("status", "collecting").set("threshold_json", thresholds.toString())
                .set("create_time", new Date()).set("update_time", new Date()));
        return store.findMonitor(id);
    }

    private JSONObject collect(Entity monitor) throws SQLException {
        JSONObject thresholds = parseObj(monitor.getStr("threshold_json"));
        long floor = thresholds == null ? 0 : longValue(thresholds.getLong("publicationFloor"));
        List<Entity> candidates = store.findEligibleItems(
                monitor.getLong("published_prompt_version_id"), floor, Math.max(minimumSamples * 5, 20));
        List<Entity> selected = new ArrayList<>();
        for (Entity item : candidates) {
            if (selected.size() == minimumSamples) break;
            try {
                JSONObject published = parseObj(item.getStr("ai_evaluation_json"));
                JSONObject advisor = parseObj(item.getStr("advisor_evaluation_json"));
                if (published == null || advisor == null || StrUtil.isBlank(item.getStr("image_url"))) continue;
                WardrobeEvaluationNormalizer.Comparison comparison = normalizer.compare(published, advisor);
                selected.add(Entity.create(RESULT_TABLE)
                        .set("monitor_id", monitor.getLong("id"))
                        .set("wardrobe_item_id", item.getLong("id"))
                        .set("status", "pending").set("attempt_count", 0)
                        .set("published_evaluation_json", comparison.getAiEvaluation().toString())
                        .set("advisor_evaluation_json", comparison.getAdvisorEvaluation().toString())
                        .set("published_diff_json", comparison.getDiffJson().toString())
                        .set("create_time", new Date()));
            } catch (RuntimeException invalid) {
                log.warn("Skipping invalid Prompt release sample itemId={}", item.getLong("id"));
            }
        }
        if (selected.size() < minimumSamples) return action("collecting", monitor);
        JSONArray ids = new JSONArray();
        long maximumId = 0;
        for (Entity row : selected) {
            Long itemId = row.getLong("wardrobe_item_id");
            ids.add(itemId);
            maximumId = Math.max(maximumId, longValue(itemId));
        }
        store.freeze(monitor.getLong("id"), selected, ids, maximumId);
        return action("samples_frozen", store.findMonitor(monitor.getLong("id")));
    }

    private JSONObject evaluate(Entity monitor) throws Exception {
        Entity result = store.claimNextResult(monitor.getLong("id"), shadowRetries);
        if (result != null) {
            try {
                JSONObject advisor = parseObj(result.getStr("advisor_evaluation_json"));
                WardrobePromptVersionService.PromptVersion baseline =
                        prompts.get(monitor.getLong("baseline_prompt_version_id"));
                JSONObject prediction = gemini.recognize(
                        result.getStr("image_url"), baseline.getPromptText()).getEvaluationJson();
                WardrobeEvaluationNormalizer.Comparison comparison = normalizer.compare(prediction, advisor);
                store.completeResult(result.getLong("id"), comparison.getAiEvaluation(), comparison.getDiffJson());
            } catch (Exception error) {
                store.failResult(result.getLong("id"), safeMessage(error));
            }
            return action("result_processed", monitor);
        }

        List<Entity> results = store.listResults(monitor.getLong("id"));
        JSONObject summary = summarize(results);
        if (intValue(summary.getInt("pending")) + intValue(summary.getInt("running")) > 0) {
            return action("evaluating", monitor);
        }
        store.recordMetrics(monitor.getLong("id"), summary);
        int requiredSuccessful = Math.max(1,
                (int) Math.ceil(minimumSamples * (1.0 - maximumFailureRate)));
        WardrobePromptRollbackPolicy.Decision decision = policy.evaluate(summary, requiredSuccessful,
                maximumFailureRate, minimumExactRegression, minimumStyleMaeRegression);
        if (decision.shouldRollback()) {
            prompts.autoRollback(monitor.getLong("baseline_prompt_version_id"),
                    monitor.getLong("published_prompt_version_id"), monitor.getLong("id"),
                    monitor.getLong("sample_max_item_id"), decision.getReason());
            Entity refreshed = store.findMonitor(monitor.getLong("id"));
            if (refreshed != null && "evaluating".equals(refreshed.getStr("status"))) {
                store.finish(monitor.getLong("id"), "evaluating", "rolled_back", summary, decision.getReason());
            }
            return action("rolled_back", store.findMonitor(monitor.getLong("id")));
        }
        int total = intValue(summary.getInt("total"));
        int failed = intValue(summary.getInt("failed"));
        boolean excessiveFailures = total > 0 && (double) failed / total > maximumFailureRate;
        String target = excessiveFailures ? "failed" : "kept";
        store.finish(monitor.getLong("id"), "evaluating", target, summary, decision.getReason());
        return action(target, store.findMonitor(monitor.getLong("id")));
    }

    private void runUntilIdle() {
        try {
            for (int i = 0; i < Math.max(20, minimumSamples * (shadowRetries + 2) + 5); i++) {
                String action;
                try {
                    action = advanceOnce().getStr("action");
                } catch (Exception error) {
                    log.error("Prompt release monitor failed", error);
                    return;
                }
                if (List.of("idle", "collecting", "evaluating", "kept", "rolled_back", "failed", "stale")
                        .contains(action)) return;
            }
        } finally {
            scheduled.set(false);
        }
    }

    private JSONObject summarize(List<Entity> rows) {
        int pending = 0;
        int running = 0;
        int completed = 0;
        int failed = 0;
        List<Entity> completedRows = new ArrayList<>();
        for (Entity row : rows) {
            String status = row.getStr("status");
            if ("pending".equals(status)) pending++;
            else if ("running".equals(status)) running++;
            else if ("completed".equals(status)) {
                completed++;
                completedRows.add(row);
            } else if ("failed".equals(status)) failed++;
        }
        return new JSONObject().set("total", rows.size()).set("pending", pending).set("running", running)
                .set("completed", completed).set("failed", failed)
                .set("publishedMetrics", metrics(completedRows,
                        "published_evaluation_json", "published_diff_json"))
                .set("baselineMetrics", metrics(completedRows,
                        "baseline_evaluation_json", "baseline_diff_json"));
    }

    private JSONObject metrics(List<Entity> rows, String evaluationColumn, String diffColumn) {
        int exact = 0;
        double absoluteError = 0;
        int values = 0;
        for (Entity row : rows) {
            JSONObject diff = parseObj(row.getStr(diffColumn));
            if (diff != null && diff.isEmpty()) exact++;
            JSONObject evaluation = parseObj(row.getStr(evaluationColumn));
            JSONObject advisor = parseObj(row.getStr("advisor_evaluation_json"));
            if (evaluation == null || advisor == null) continue;
            Map<String, Integer> predicted = styleScores(evaluation.getJSONArray("types"));
            Map<String, Integer> expected = styleScores(advisor.getJSONArray("types"));
            for (String style : STYLES) {
                absoluteError += Math.abs(predicted.get(style) - expected.get(style));
                values++;
            }
        }
        double mae = values == 0 ? 0 : absoluteError / values;
        double exactRate = rows.isEmpty() ? 0 : exact * 1.0 / rows.size();
        return new JSONObject().set("sampleCount", rows.size()).set("exactMatches", exact)
                .set("exactMatchRate", exactRate).set("styleMae", Math.round(mae * 100.0) / 100.0);
    }

    private Map<String, Integer> styleScores(JSONArray values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        STYLES.forEach(style -> result.put(style, 0));
        if (values == null) return result;
        for (Object value : values) {
            JSONObject item = value instanceof JSONObject ? (JSONObject) value : new JSONObject(value);
            String style = item.getStr("type");
            String raw = item.getStr("typeProportion");
            if (result.containsKey(style) && raw != null && raw.matches("\\d+%")) {
                result.put(style, Integer.parseInt(raw.substring(0, raw.length() - 1)));
            }
        }
        return result;
    }

    private JSONObject action(String action, Entity monitor) {
        return new JSONObject().set("action", action)
                .set("monitorId", monitor == null ? null : monitor.getLong("id"));
    }

    private JSONObject parseObj(String value) {
        return StrUtil.isBlank(value) ? null : JSONUtil.parseObj(value);
    }

    private long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (StrUtil.isBlank(message)) return "Prompt shadow evaluation failed";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public interface Store {
        Entity latestAutomaticPublication() throws SQLException;
        Entity findMonitorByPublication(Long auditId) throws SQLException;
        Entity findActiveMonitor() throws SQLException;
        Long insertMonitor(Entity monitor) throws SQLException;
        Entity findMonitor(Long id) throws SQLException;
        List<Entity> findEligibleItems(Long promptVersionId, long floor, int limit) throws SQLException;
        boolean freeze(Long monitorId, List<Entity> selected, JSONArray ids, long maximumId) throws SQLException;
        Entity claimNextResult(Long monitorId, int maximumRetries) throws SQLException;
        void completeResult(Long resultId, JSONObject baseline, JSONObject baselineDiff) throws SQLException;
        void failResult(Long resultId, String error) throws SQLException;
        List<Entity> listResults(Long monitorId) throws SQLException;
        void recordMetrics(Long monitorId, JSONObject summary) throws SQLException;
        boolean finish(Long monitorId, String fromStatus, String toStatus,
                       JSONObject summary, String reason) throws SQLException;
        boolean markStale(Long monitorId, String reason) throws SQLException;
        boolean blocksLatestAutomaticPublication() throws SQLException;
    }

    @Service
    public static final class HutoolStore implements Store {
        private Db db() {
            return DBConfig.getDb(null, "qj");
        }

        @Override
        public Entity latestAutomaticPublication() throws SQLException {
            Entity row = db().queryOne("SELECT a.*, COALESCE(a.sample_floor_item_id, o.sample_max_item_id, 0)"
                    + " AS publication_floor FROM qj_wardrobe_prompt_version_audit a"
                    + " LEFT JOIN qj_wardrobe_prompt_optimization o ON o.id = a.optimization_id"
                    + " ORDER BY a.id DESC LIMIT 1");
            return row != null && "auto_publish".equals(row.getStr("action")) ? row : null;
        }

        @Override
        public Entity findMonitorByPublication(Long auditId) throws SQLException {
            return auditId == null ? null : db().queryOne("SELECT * FROM " + MONITOR_TABLE
                    + " WHERE publication_audit_id = ?", auditId);
        }

        @Override
        public Entity findActiveMonitor() throws SQLException {
            return db().queryOne("SELECT * FROM " + MONITOR_TABLE
                    + " WHERE status IN ('collecting','evaluating') ORDER BY id DESC LIMIT 1");
        }

        @Override
        public Long insertMonitor(Entity monitor) throws SQLException {
            try {
                return db().insertForGeneratedKey(monitor);
            } catch (SQLException duplicate) {
                if (duplicate.getErrorCode() == 1062) {
                    Entity existing = findMonitorByPublication(monitor.getLong("publication_audit_id"));
                    if (existing != null) return existing.getLong("id");
                }
                throw duplicate;
            }
        }

        @Override
        public Entity findMonitor(Long id) throws SQLException {
            return id == null ? null : db().queryOne("SELECT * FROM " + MONITOR_TABLE + " WHERE id = ?", id);
        }

        @Override
        public List<Entity> findEligibleItems(Long promptVersionId, long floor, int limit) throws SQLException {
            return new ArrayList<>(db().query("SELECT id, image_url, prompt_version_id, ai_evaluation_json,"
                            + " advisor_evaluation_json FROM qj_wardrobe_item"
                            + " WHERE status = 'completed' AND prompt_version_id = ? AND id > ?"
                            + " AND ai_evaluation_json IS NOT NULL AND advisor_evaluation_json IS NOT NULL"
                            + " ORDER BY id ASC LIMIT ?", promptVersionId, floor, limit));
        }

        @Override
        public boolean freeze(Long monitorId, List<Entity> selected, JSONArray ids, long maximumId)
                throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                int changed = session.execute("UPDATE " + MONITOR_TABLE
                                + " SET status = 'evaluating', sample_ids_json = ?, sample_max_item_id = ?,"
                                + " evaluation_start_time = NOW(), update_time = NOW()"
                                + " WHERE id = ? AND status = 'collecting'",
                        ids.toString(), maximumId, monitorId);
                if (changed != 1) {
                    session.rollback();
                    return false;
                }
                for (Entity row : selected) session.insert(copyForInsert(row, RESULT_TABLE));
                session.commit();
                return true;
            } catch (SQLException | RuntimeException error) {
                try { session.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                if (error instanceof SQLException) throw (SQLException) error;
                throw (RuntimeException) error;
            } finally {
                session.close();
            }
        }

        @Override
        public Entity claimNextResult(Long monitorId, int maximumRetries) throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                Entity row = session.queryOne("SELECT r.*, i.image_url FROM " + RESULT_TABLE
                                + " r JOIN qj_wardrobe_item i ON i.id = r.wardrobe_item_id"
                                + " WHERE r.monitor_id = ? AND ((r.status IN ('pending','failed')"
                                + " AND r.attempt_count <= ?) OR (r.status = 'running'"
                                + " AND r.attempt_count <= ? AND r.start_time < DATE_SUB(NOW(), INTERVAL 2 MINUTE)))"
                                + " ORDER BY r.id LIMIT 1 FOR UPDATE",
                        monitorId, maximumRetries, maximumRetries);
                if (row == null) {
                    session.commit();
                    return null;
                }
                int changed = session.execute("UPDATE " + RESULT_TABLE
                                + " SET status = 'running', attempt_count = attempt_count + 1,"
                                + " error_msg = NULL, start_time = NOW(), finish_time = NULL WHERE id = ?",
                        row.getLong("id"));
                if (changed != 1) {
                    session.rollback();
                    return null;
                }
                Integer attemptCount = row.getInt("attempt_count");
                row.set("status", "running").set("attempt_count",
                        (attemptCount == null ? 0 : attemptCount) + 1);
                session.commit();
                return row;
            } catch (SQLException | RuntimeException error) {
                try { session.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                if (error instanceof SQLException) throw (SQLException) error;
                throw (RuntimeException) error;
            } finally {
                session.close();
            }
        }

        @Override
        public void completeResult(Long resultId, JSONObject baseline, JSONObject baselineDiff) throws SQLException {
            db().execute("UPDATE " + RESULT_TABLE + " SET status = 'completed',"
                            + " baseline_evaluation_json = ?, baseline_diff_json = ?, error_msg = NULL,"
                            + " finish_time = NOW() WHERE id = ? AND status = 'running'",
                    baseline.toString(), baselineDiff.toString(), resultId);
        }

        @Override
        public void failResult(Long resultId, String error) throws SQLException {
            db().execute("UPDATE " + RESULT_TABLE + " SET status = 'failed', error_msg = ?,"
                    + " finish_time = NOW() WHERE id = ? AND status = 'running'", error, resultId);
        }

        @Override
        public List<Entity> listResults(Long monitorId) throws SQLException {
            return new ArrayList<>(db().query("SELECT * FROM " + RESULT_TABLE
                    + " WHERE monitor_id = ? ORDER BY id", monitorId));
        }

        @Override
        public void recordMetrics(Long monitorId, JSONObject summary) throws SQLException {
            db().execute("UPDATE " + MONITOR_TABLE + " SET published_metrics_json = ?,"
                            + " baseline_metrics_json = ?, update_time = NOW()"
                            + " WHERE id = ? AND status = 'evaluating'",
                    summary.getJSONObject("publishedMetrics").toString(),
                    summary.getJSONObject("baselineMetrics").toString(), monitorId);
        }

        @Override
        public boolean finish(Long monitorId, String fromStatus, String toStatus,
                              JSONObject summary, String reason) throws SQLException {
            return db().execute("UPDATE " + MONITOR_TABLE + " SET status = ?,"
                            + " published_metrics_json = ?, baseline_metrics_json = ?, decision_reason = ?,"
                            + " finish_time = NOW(), update_time = NOW() WHERE id = ? AND status = ?",
                    toStatus, summary.getJSONObject("publishedMetrics").toString(),
                    summary.getJSONObject("baselineMetrics").toString(), reason, monitorId, fromStatus) == 1;
        }

        @Override
        public boolean markStale(Long monitorId, String reason) throws SQLException {
            return db().execute("UPDATE " + MONITOR_TABLE + " SET status = 'stale', decision_reason = ?,"
                    + " finish_time = NOW(), update_time = NOW()"
                    + " WHERE id = ? AND status IN ('collecting','evaluating')", reason, monitorId) == 1;
        }

        @Override
        public boolean blocksLatestAutomaticPublication() throws SQLException {
            Entity row = db().queryOne("SELECT a.action, m.status FROM qj_wardrobe_prompt_version_audit a"
                    + " LEFT JOIN " + MONITOR_TABLE + " m ON m.publication_audit_id = a.id"
                    + " ORDER BY a.id DESC LIMIT 1");
            if (row == null || !"auto_publish".equals(row.getStr("action"))) return false;
            String status = row.getStr("status");
            return status == null || "collecting".equals(status) || "evaluating".equals(status);
        }

        private Session newSession() {
            String key = DBConfig.getDbSetting().getStr("db_env") + "_qj";
            return Session.create(DSFactory.get(key));
        }

        private Entity copyForInsert(Entity source, String table) {
            Entity target = Entity.create(table);
            source.forEach((key, value) -> {
                if (!key.startsWith("_")) target.set(key, value);
            });
            return target;
        }
    }
}
