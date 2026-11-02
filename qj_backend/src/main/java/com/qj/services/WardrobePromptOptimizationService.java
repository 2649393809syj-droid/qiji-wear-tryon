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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates immutable manual and automatic Prompt optimization jobs without publishing them. */
@Service
public class WardrobePromptOptimizationService {

    private static final String TABLE = "qj_wardrobe_prompt_optimization";
    private final Store store;
    private final WardrobePromptVersionService promptService;
    private final WardrobePromptCandidateGenerator generator;
    private final WardrobeEvaluationNormalizer normalizer;
    private final int minimumSamples;
    private final int maximumSamples;
    private final int minimumNewSamples;
    private final int minimumPositive;
    private final int minimumNegative;
    private final int minimumHeldout;
    private final int maximumTrainingSamples;

    @Autowired
    public WardrobePromptOptimizationService(Store store,
                                             WardrobePromptVersionService promptService,
                                             WardrobePromptCandidateGenerator generator,
                                             @Value("${wardrobe.prompt.minimum-samples:4}") int minimumSamples,
                                             @Value("${wardrobe.prompt.maximum-samples:50}") int maximumSamples,
                                             @Value("${wardrobe.prompt.auto.minimum-new-samples:4}") int minimumNewSamples,
                                             @Value("${wardrobe.prompt.auto.minimum-positive:1}") int minimumPositive,
                                             @Value("${wardrobe.prompt.auto.minimum-negative:1}") int minimumNegative,
                                             @Value("${wardrobe.prompt.auto.minimum-heldout:2}") int minimumHeldout,
                                             @Value("${wardrobe.prompt.auto.maximum-training-samples:50}") int maximumTrainingSamples) {
        this.store = store;
        this.promptService = promptService;
        this.generator = generator;
        this.normalizer = new WardrobeEvaluationNormalizer();
        this.minimumSamples = Math.max(2, minimumSamples);
        this.maximumSamples = Math.max(this.minimumSamples, maximumSamples);
        validateAutomaticConfig(minimumNewSamples, minimumPositive, minimumNegative, minimumHeldout,
                maximumTrainingSamples);
        this.minimumNewSamples = minimumNewSamples;
        this.minimumPositive = minimumPositive;
        this.minimumNegative = minimumNegative;
        this.minimumHeldout = minimumHeldout;
        this.maximumTrainingSamples = maximumTrainingSamples;
    }

    WardrobePromptOptimizationService(Store store, WardrobePromptVersionService promptService,
                                      WardrobePromptCandidateGenerator generator, int minimumSamples,
                                      int maximumSamples) {
        this(store, promptService, generator, minimumSamples, maximumSamples, 4, 1, 1, 2, 50);
    }

    WardrobePromptOptimizationService(Store store, WardrobePromptVersionService promptService,
                                      WardrobePromptCandidateGenerator generator, int minimumNewSamples,
                                      int minimumPositive, int minimumNegative, int minimumHeldout,
                                      int maximumTrainingSamples) {
        this(store, promptService, generator, minimumNewSamples, maximumTrainingSamples, minimumNewSamples,
                minimumPositive, minimumNegative, minimumHeldout, maximumTrainingSamples);
    }

    public JSONObject start(JSONObject filter, String createdBy) throws SQLException {
        if (StrUtil.isBlank(createdBy)) throw new IllegalArgumentException("createdBy is required");
        JSONObject safeFilter = filter == null ? new JSONObject() : JSONUtil.parseObj(filter.toString());
        List<Entity> samples = store.findReviewedSamples(safeFilter, maximumSamples);
        validateSamples(samples, minimumSamples, 1, 1);
        WardrobePromptVersionService.PromptVersion active = promptService.getActivePrompt();
        JSONArray sampleIds = ids(samples);
        Long jobId = store.insertJob(Entity.create(TABLE)
                .set("status", "pending").set("base_prompt_version_id", active.getId())
                .set("filter_json", safeFilter.toString()).set("sample_ids_json", sampleIds.toString())
                .set("created_by", createdBy.trim()).set("create_time", new Date()));
        try {
            store.markRunning(jobId);
            WardrobePromptCandidateGenerator.CandidateResult generated = generator.generate(
                    active.getPromptText(), aggregate(samples), compact(samples));
            WardrobePromptVersionService.PromptVersion candidate = promptService.createCandidate(
                    candidateName(jobId), generated.getCandidatePrompt(), active.getId(), "ai_generated",
                    generated.getSummary(), createdBy.trim());
            store.markCompleted(jobId, candidate.getId(), generated.getSummary());
        } catch (Exception error) {
            store.markFailed(jobId, safeMessage(error));
        }
        return getJob(jobId);
    }

    /** Starts one automatic job from reviewed snapshots newer than the latest automatic or rollback floor. */
    public JSONObject startAutomatic() throws SQLException {
        Entity existing = store.findActiveAutomaticJob();
        if (existing != null) {
            existing = staleIfBaseChanged(existing);
            if (isRunning(existing)) return toJson(existing);
        }
        if (store.releaseMonitorBlocksOptimization()) return null;

        long rollbackFloor = store.latestRollbackSampleFloor();
        long automaticFloor = store.latestAutomaticSampleFloor();
        long floor = Math.max(automaticFloor, rollbackFloor);
        List<Entity> candidates = collectValidAutomaticSamples(floor, maximumTrainingSamples + minimumHeldout);
        if (candidates.size() < minimumNewSamples) return null;
        if (!hasAlignedSnapshots(candidates)) return null;

        List<Entity> heldout = new ArrayList<>();
        List<Entity> training = new ArrayList<>(candidates);
        if (candidates.size() >= minimumNewSamples + minimumHeldout) {
            heldout.addAll(candidates.subList(candidates.size() - minimumHeldout, candidates.size()));
            training = new ArrayList<>(candidates.subList(0, candidates.size() - minimumHeldout));
        }
        if (training.size() > maximumTrainingSamples) {
            training = new ArrayList<>(training.subList(0, maximumTrainingSamples));
        }
        if (!hasSampleMix(training, minimumPositive, minimumNegative)) return null;
        ensureDisjoint(training, heldout);

        WardrobePromptVersionService.PromptVersion active = promptService.getActivePrompt();
        JSONArray sampleIds = ids(training);
        JSONArray heldoutIds = ids(heldout);
        Entity job = Entity.create(TABLE)
                .set("trigger_type", "automatic")
                .set("status", heldout.size() == minimumHeldout ? "generating" : "waiting_for_holdout")
                .set("base_prompt_version_id", active.getId())
                .set("sample_ids_json", sampleIds.toString())
                .set("heldout_sample_ids_json", heldoutIds.toString())
                .set("threshold_json", thresholds().toString())
                .set("sample_max_item_id", maximumId(candidates))
                .set("created_by", "system:auto-optimizer")
                .set("create_time", new Date());
        try {
            Long id = store.insertAutomaticJob(job, active.getId(), rollbackFloor, automaticFloor);
            if (id == null) return null;
            return getJob(id);
        } catch (SQLException | RuntimeException duplicate) {
            if (isRunningGuardDuplicate(duplicate)) {
                Entity running = store.findActiveAutomaticJob();
                if (running != null) return toJson(running);
            }
            if (duplicate instanceof SQLException) throw (SQLException) duplicate;
            throw (RuntimeException) duplicate;
        }
    }

    /** Adds only newly arrived, disjoint held-out snapshots to a waiting job. */
    public JSONObject refreshHeldout(Long jobId) throws SQLException {
        Entity job = store.findJob(jobId);
        if (job == null || !"automatic".equals(job.getStr("trigger_type"))
                || !"waiting_for_holdout".equals(job.getStr("status"))) return null;
        job = staleIfBaseChanged(job);
        if (!"waiting_for_holdout".equals(job.getStr("status"))) return toJson(job);
        JSONArray trainingIds = parseArray(job.getStr("sample_ids_json"));
        int requiredHeldout = heldoutThreshold(job);
        List<Entity> candidates = collectValidAutomaticSamples(value(job.getLong("sample_max_item_id")), requiredHeldout);
        candidates.sort((left, right) -> {
            int compared = left.getDate("review_time").compareTo(right.getDate("review_time"));
            return compared != 0 ? compared : Long.compare(left.getLong("id"), right.getLong("id"));
        });
        if (candidates.size() < requiredHeldout || !hasAlignedSnapshots(candidates)
                || overlaps(trainingIds, ids(candidates))) return getJob(jobId);
        if (!store.transition(jobId, "waiting_for_holdout", "generating", Entity.create(TABLE)
                .set("heldout_sample_ids_json", ids(candidates).toString())
                .set("sample_max_item_id", maximumId(candidates)))) return getJob(jobId);
        return getJob(jobId);
    }

    /** Generates or recovers the deterministic candidate for a generating automatic job. */
    public JSONObject resumeGeneration(Long jobId) throws SQLException {
        Entity job = store.findJob(jobId);
        if (job == null || !"automatic".equals(job.getStr("trigger_type"))) return job == null ? null : toJson(job);
        job = staleIfBaseChanged(job);
        if (!"generating".equals(job.getStr("status"))) return toJson(job);
        if (job.getLong("candidate_prompt_version_id") != null) return toJson(job);

        String name = candidateName(jobId);
        WardrobePromptVersionService.PromptVersion candidate = promptService.findByVersionName(name);
        if (candidate != null) {
            if (!matches(job, candidate)) return fail(job, "Deterministic Prompt candidate does not match its job base");
            store.attachCandidate(jobId, "generating", candidate.getId(), candidate.getSummary());
            return getJob(jobId);
        }

        try {
            List<Entity> samples = snapshotSamples(parseArray(job.getStr("sample_ids_json")));
            WardrobePromptVersionService.PromptVersion active = promptService.get(job.getLong("base_prompt_version_id"));
            WardrobePromptCandidateGenerator.CandidateResult generated = generator.generate(
                    active.getPromptText(), aggregate(samples), compact(samples));
            candidate = promptService.createCandidate(name, generated.getCandidatePrompt(), active.getId(),
                    "ai_generated", generated.getSummary(), "system:auto-optimizer");
            job = staleIfBaseChanged(job);
            if (!"generating".equals(job.getStr("status"))) return toJson(job);
            if (!matches(job, candidate)) return fail(job, "Generated Prompt candidate does not match its job base");
            store.attachCandidate(jobId, "generating", candidate.getId(), generated.getSummary());
        } catch (Exception error) {
            WardrobePromptVersionService.PromptVersion recovered = promptService.findByVersionName(name);
            if (recovered != null && matches(job, recovered)) {
                job = staleIfBaseChanged(job);
                if (!"generating".equals(job.getStr("status"))) return toJson(job);
                store.attachCandidate(jobId, "generating", recovered.getId(), recovered.getSummary());
            } else {
                return fail(job, safeMessage(error));
            }
        }
        return getJob(jobId);
    }

    public JSONObject markEvaluating(Long jobId) throws SQLException {
        Entity job = requiredAutomatic(jobId, "generating");
        job = staleIfBaseChanged(job);
        if (!"generating".equals(job.getStr("status"))) return toJson(job);
        if (job.getLong("candidate_prompt_version_id") == null) {
            throw new IllegalStateException("Automatic Prompt candidate has not been generated");
        }
        store.transition(jobId, "generating", "evaluating", null);
        return getJob(jobId);
    }

    public JSONObject markRejected(Long jobId, String reason) throws SQLException {
        Entity job = requiredAutomatic(jobId, "evaluating");
        job = staleIfBaseChanged(job);
        if (!"evaluating".equals(job.getStr("status"))) return toJson(job);
        store.transition(jobId, "evaluating", "rejected", Entity.create(TABLE)
                .set("decision_reason", StrUtil.trim(reason)).set("finish_time", new Date()));
        return getJob(jobId);
    }

    /** Persists experiment metrics before the policy makes the terminal job decision. */
    public JSONObject recordEvaluation(Long jobId, JSONObject summary) throws SQLException {
        Entity job = requiredAutomatic(jobId, "evaluating");
        JSONObject baseline = summary == null ? null : summary.getJSONObject("baselineMetrics");
        JSONObject candidate = summary == null ? null : summary.getJSONObject("candidateMetrics");
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("Completed Prompt evaluation metrics are required");
        }
        store.transition(jobId, "evaluating", "evaluating", Entity.create(TABLE)
                .set("baseline_metrics_json", baseline.toString())
                .set("candidate_metrics_json", candidate.toString()));
        return getJob(jobId);
    }

    public JSONObject markStale(Long jobId) throws SQLException {
        Entity job = store.findJob(jobId);
        if (job == null || !"automatic".equals(job.getStr("trigger_type"))) return null;
        String status = job.getStr("status");
        if (!"waiting_for_holdout".equals(status) && !"generating".equals(status)
                && !"evaluating".equals(status)) return toJson(job);
        store.transition(jobId, status, "stale", Entity.create(TABLE).set("finish_time", new Date()));
        return getJob(jobId);
    }

    public JSONObject markFailed(Long jobId, String expectedStatus, String error) throws SQLException {
        if (StrUtil.isBlank(expectedStatus)) throw new IllegalArgumentException("expectedStatus is required");
        Entity job = requiredAutomatic(jobId, expectedStatus);
        job = staleIfBaseChanged(job);
        if (!expectedStatus.equals(job.getStr("status"))) return toJson(job);
        store.transition(jobId, expectedStatus, "failed", Entity.create(TABLE)
                .set("error_msg", safeMessage(new IllegalStateException(error))).set("finish_time", new Date()));
        return getJob(jobId);
    }

    public JSONObject getActiveAutomaticJob() throws SQLException {
        Entity job = store.findActiveAutomaticJob();
        return job == null ? null : toJson(job);
    }

    public JSONArray listJobs(int limit) throws SQLException {
        JSONArray jobs = new JSONArray();
        for (Entity row : store.listAutomaticJobs(Math.max(1, Math.min(limit, 100)))) jobs.add(toJson(row));
        return jobs;
    }

    public JSONObject getJob(Long id) throws SQLException {
        Entity row = store.findJob(id);
        if (row == null) throw new IllegalArgumentException("Prompt optimization job does not exist: " + id);
        return toJson(row);
    }

    private JSONObject fail(Entity job, String error) throws SQLException {
        store.transition(job.getLong("id"), "generating", "failed", Entity.create(TABLE)
                .set("error_msg", safeMessage(new IllegalStateException(error))).set("finish_time", new Date()));
        return getJob(job.getLong("id"));
    }

    private Entity requiredAutomatic(Long jobId, String status) throws SQLException {
        Entity job = store.findJob(jobId);
        if (job == null || !"automatic".equals(job.getStr("trigger_type")) || !status.equals(job.getStr("status"))) {
            throw new IllegalStateException("Automatic Prompt optimization is no longer " + status);
        }
        return job;
    }

    private List<Entity> snapshotSamples(JSONArray ids) throws SQLException {
        List<Entity> rows = store.findSamplesByIds(ids);
        Map<Long, Entity> byId = new LinkedHashMap<>();
        for (Entity row : rows) byId.put(row.getLong("id"), row);
        List<Entity> ordered = new ArrayList<>();
        for (Long id : ids.toList(Long.class)) {
            Entity row = byId.get(id);
            if (row == null) throw new IllegalStateException("Reviewed sample snapshot is missing: " + id);
            ordered.add(row);
        }
        return ordered;
    }

    private JSONObject toJson(Entity row) {
        return new JSONObject()
                .set("id", row.getLong("id")).set("triggerType", row.getStr("trigger_type"))
                .set("status", row.getStr("status")).set("basePromptVersionId", row.getLong("base_prompt_version_id"))
                .set("candidatePromptVersionId", row.getLong("candidate_prompt_version_id"))
                .set("filter", parseObj(row.getStr("filter_json"))).set("sampleIds", parseArray(row.getStr("sample_ids_json")))
                .set("heldoutSampleIds", parseArray(row.getStr("heldout_sample_ids_json")))
                .set("thresholds", parseObj(row.getStr("threshold_json"))).set("sampleMaxItemId", row.getLong("sample_max_item_id"))
                .set("baselineMetrics", parseObj(row.getStr("baseline_metrics_json")))
                .set("candidateMetrics", parseObj(row.getStr("candidate_metrics_json")))
                .set("analysisSummary", row.getStr("analysis_summary")).set("decisionReason", row.getStr("decision_reason"))
                .set("errorMsg", row.getStr("error_msg")).set("createdBy", row.getStr("created_by"))
                .set("createTime", row.getDate("create_time")).set("startTime", row.getDate("start_time"))
                .set("finishTime", row.getDate("finish_time"));
    }

    private JSONObject thresholds() {
        return new JSONObject().set("minimumNewSamples", minimumNewSamples).set("minimumPositive", minimumPositive)
                .set("minimumNegative", minimumNegative).set("minimumHeldout", minimumHeldout)
                .set("maximumTrainingSamples", maximumTrainingSamples);
    }

    private void validateSamples(List<Entity> samples, int minimum, int positives, int negatives) {
        if (samples == null || samples.size() < minimum || !hasSampleMix(samples, positives, negatives)) {
            throw new IllegalArgumentException("Both positive and negative reviewed samples are required");
        }
    }

    private boolean hasSampleMix(List<Entity> samples, int positives, int negatives) {
        int positive = 0;
        int negative = 0;
        for (Entity sample : samples) {
            if ("positive".equals(sample.getStr("sample_type"))) positive++;
            if ("negative".equals(sample.getStr("sample_type"))) negative++;
        }
        return positive >= positives && negative >= negatives;
    }

    private boolean hasAlignedSnapshots(List<Entity> samples) {
        try {
            for (Entity sample : samples) {
                JSONObject ai = parseObj(sample.getStr("ai_evaluation_json"));
                JSONObject advisor = parseObj(sample.getStr("advisor_evaluation_json"));
                if (ai == null || advisor == null) return false;
                normalizer.validateAlignedSnapshot(ai);
                normalizer.validateAlignedSnapshot(advisor);
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<Entity> collectValidAutomaticSamples(long floor, int target) throws SQLException {
        List<Entity> valid = new ArrayList<>();
        long cursor = floor;
        int pageSize = Math.min(100, Math.max(16, target));
        while (valid.size() < target) {
            List<Entity> page = store.findAutomaticSamples(cursor, pageSize);
            if (page.isEmpty()) break;
            for (Entity row : page) {
                cursor = Math.max(cursor, value(row.getLong("id")));
                if (hasAlignedSnapshots(java.util.Collections.singletonList(row))) valid.add(row);
                if (valid.size() == target) break;
            }
            if (page.size() < pageSize) break;
        }
        valid.sort((left, right) -> {
            int compared = right.getDate("review_time").compareTo(left.getDate("review_time"));
            return compared != 0 ? compared : Long.compare(right.getLong("id"), left.getLong("id"));
        });
        return valid;
    }

    private JSONObject aggregate(List<Entity> samples) {
        int positive = 0;
        int negative = 0;
        Map<String, Integer> diffKinds = new LinkedHashMap<>();
        for (Entity sample : samples) {
            if ("positive".equals(sample.getStr("sample_type"))) positive++;
            if ("negative".equals(sample.getStr("sample_type"))) negative++;
            JSONObject diff = parseObj(sample.getStr("diff_json"));
            if (diff != null) for (String field : diff.keySet()) {
                JSONObject detail = diff.getJSONObject(field);
                String kind = detail == null ? "changed" : StrUtil.blankToDefault(detail.getStr("kind"), "changed");
                diffKinds.put(kind, diffKinds.getOrDefault(kind, 0) + 1);
            }
        }
        return new JSONObject().set("sampleCount", samples.size()).set("positiveCount", positive)
                .set("negativeCount", negative).set("diffKindCounts", diffKinds);
    }

    private JSONArray compact(List<Entity> samples) {
        JSONArray result = new JSONArray();
        for (Entity sample : samples) result.add(new JSONObject().set("id", sample.getLong("id"))
                .set("sampleType", sample.getStr("sample_type")).set("navItemCur", sample.getInt("nav_item_cur"))
                .set("aiEvaluation", parseObj(sample.getStr("ai_evaluation_json")))
                .set("advisorEvaluation", parseObj(sample.getStr("advisor_evaluation_json")))
                .set("diff", parseObj(sample.getStr("diff_json"))));
        return result;
    }

    private JSONArray ids(List<Entity> samples) {
        JSONArray result = new JSONArray();
        for (Entity sample : samples) result.add(sample.getLong("id"));
        return result;
    }

    private void ensureDisjoint(List<Entity> training, List<Entity> heldout) {
        if (overlaps(ids(training), ids(heldout))) throw new IllegalStateException("Training and held-out snapshots overlap");
    }

    private boolean overlaps(JSONArray left, JSONArray right) {
        Set<Long> ids = new LinkedHashSet<>(left.toList(Long.class));
        for (Long id : right.toList(Long.class)) if (!ids.add(id)) return true;
        return false;
    }

    private long maximumId(List<Entity> rows) {
        long maximum = 0;
        for (Entity row : rows) maximum = Math.max(maximum, value(row.getLong("id")));
        return maximum;
    }

    private long value(Long number) { return number == null ? 0 : number; }

    private int heldoutThreshold(Entity job) {
        JSONObject values = parseObj(job.getStr("threshold_json"));
        Integer threshold = values == null ? null : values.getInt("minimumHeldout");
        if (threshold == null || threshold <= 0) throw new IllegalStateException("Automatic job has invalid held-out threshold");
        return threshold;
    }

    private Entity staleIfBaseChanged(Entity job) throws SQLException {
        if (job == null || !isRunning(job)) return job;
        WardrobePromptVersionService.PromptVersion active = promptService.getActivePrompt();
        if (job.getLong("base_prompt_version_id").equals(active.getId())) return job;
        store.transition(job.getLong("id"), job.getStr("status"), "stale", Entity.create(TABLE)
                .set("finish_time", new Date()));
        return store.findJob(job.getLong("id"));
    }

    private boolean isRunning(Entity job) {
        return job != null && Arrays.asList("pending", "waiting_for_holdout", "generating", "evaluating")
                .contains(job.getStr("status"));
    }

    private boolean isRunningGuardDuplicate(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException) {
                SQLException sql = (SQLException) current;
                String message = StrUtil.blankToDefault(sql.getMessage(), "").toLowerCase();
                if (sql.getErrorCode() == 1062 && message.contains("uk_prompt_optimization_one_running")) return true;
            }
        }
        return false;
    }

    private void validateAutomaticConfig(int newSamples, int positive, int negative, int heldout, int training) {
        if (newSamples <= 0 || positive <= 0 || negative <= 0 || heldout <= 0 || training <= 0
                || training < newSamples || positive + negative > training) {
            throw new IllegalArgumentException("Invalid automatic Prompt optimization thresholds");
        }
    }

    private boolean matches(Entity job, WardrobePromptVersionService.PromptVersion candidate) {
        return "ai_generated".equals(candidate.getSource())
                && job.getLong("base_prompt_version_id").equals(candidate.getParentVersionId());
    }

    private String candidateName(Long jobId) { return "wardrobe-ai-candidate-" + jobId; }
    private JSONObject parseObj(String value) { return StrUtil.isBlank(value) ? null : JSONUtil.parseObj(value); }
    private JSONArray parseArray(String value) { return StrUtil.isBlank(value) ? new JSONArray() : JSONUtil.parseArray(value); }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (StrUtil.isBlank(message)) return "Prompt candidate generation failed";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public interface Store {
        List<Entity> findReviewedSamples(JSONObject filter, int limit) throws SQLException;
        List<Entity> findAutomaticSamples(long floor, int limit) throws SQLException;
        List<Entity> findAutomaticHeldoutSamples(long floor, int limit) throws SQLException;
        List<Entity> findSamplesByIds(JSONArray ids) throws SQLException;
        Long insertJob(Entity job) throws SQLException;
        Long insertAutomaticJob(Entity job, Long expectedBaseVersionId, long expectedRollbackFloor,
                                long expectedAutomaticFloor) throws SQLException;
        void markRunning(Long id) throws SQLException;
        void markCompleted(Long id, Long candidateId, String summary) throws SQLException;
        void markFailed(Long id, String error) throws SQLException;
        Entity findJob(Long id) throws SQLException;
        Entity findActiveAutomaticJob() throws SQLException;
        List<Entity> listAutomaticJobs(int limit) throws SQLException;
        long latestAutomaticSampleFloor() throws SQLException;
        long latestRollbackSampleFloor() throws SQLException;
        default boolean releaseMonitorBlocksOptimization() throws SQLException { return false; }
        boolean transition(Long id, String from, String to, Entity values) throws SQLException;
        boolean attachCandidate(Long id, String from, Long candidateId, String summary) throws SQLException;
    }

    @Service
    public static final class HutoolStore implements Store {
        private Db db() { return DBConfig.getDb(null, "qj"); }

        @Override
        public List<Entity> findReviewedSamples(JSONObject filter, int limit) throws SQLException {
            StringBuilder sql = eligibleSql(" WHERE flow_version = 'single_pass_v2'");
            List<Object> params = new ArrayList<>();
            appendManualFilter(sql, params, filter);
            sql.append(" ORDER BY review_time DESC, id DESC LIMIT ?");
            params.add(limit);
            return new ArrayList<>(db().query(sql.toString(), params.toArray()));
        }

        @Override
        public List<Entity> findAutomaticSamples(long floor, int limit) throws SQLException {
            return new ArrayList<>(db().query(eligibleSql(" WHERE flow_version = 'single_pass_v2'")
                    .append(" AND id > ? ORDER BY id ASC LIMIT ?").toString(), floor, limit));
        }

        @Override
        public List<Entity> findAutomaticHeldoutSamples(long floor, int limit) throws SQLException {
            return new ArrayList<>(db().query(eligibleSql(" WHERE flow_version = 'single_pass_v2'")
                    .append(" AND id > ? ORDER BY review_time ASC, id ASC LIMIT ?").toString(), floor, limit));
        }

        @Override
        public List<Entity> findSamplesByIds(JSONArray ids) throws SQLException {
            if (ids == null || ids.isEmpty()) return new ArrayList<>();
            StringBuilder sql = eligibleSql(" WHERE flow_version = 'single_pass_v2'").append(" AND id IN (");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) { if (i > 0) sql.append(','); sql.append('?'); params.add(ids.getLong(i)); }
            sql.append(")");
            return new ArrayList<>(db().query(sql.toString(), params.toArray()));
        }

        @Override public Long insertJob(Entity job) throws SQLException { return db().insertForGeneratedKey(job); }
        @Override
        public Long insertAutomaticJob(Entity job, Long expectedBaseVersionId, long expectedRollbackFloor,
                                       long expectedAutomaticFloor) throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                List<Entity> active = session.query("SELECT id FROM qj_wardrobe_prompt_version"
                        + " WHERE status = 'active' FOR UPDATE");
                if (active.size() != 1 || !expectedBaseVersionId.equals(active.get(0).getLong("id"))) {
                    session.rollback();
                    return null;
                }
                Entity latestAudit = session.queryOne("SELECT id, action"
                        + " FROM qj_wardrobe_prompt_version_audit ORDER BY id DESC LIMIT 1 FOR UPDATE");
                if (latestAudit != null && "auto_publish".equals(latestAudit.getStr("action"))) {
                    Entity monitor = session.queryOne("SELECT status FROM qj_wardrobe_prompt_release_monitor"
                            + " WHERE publication_audit_id = ? FOR UPDATE", latestAudit.getLong("id"));
                    if (monitor == null || Arrays.asList("collecting", "evaluating")
                            .contains(monitor.getStr("status"))) {
                        session.rollback();
                        return null;
                    }
                }
                Entity rollback = session.queryOne("SELECT COALESCE(sample_floor_item_id, 0) AS sample_floor"
                        + " FROM qj_wardrobe_prompt_version_audit"
                        + " WHERE action IN ('manual_rollback', 'auto_rollback')"
                        + " ORDER BY id DESC LIMIT 1 FOR UPDATE");
                long currentFloor = rollback == null || rollback.getLong("sample_floor") == null
                        ? 0 : rollback.getLong("sample_floor");
                if (currentFloor != expectedRollbackFloor) {
                    session.rollback();
                    return null;
                }
                Entity automatic = session.queryOne("SELECT COALESCE(MAX(sample_max_item_id), 0) AS sample_floor"
                        + " FROM " + TABLE + " WHERE trigger_type = 'automatic' FOR UPDATE");
                long currentAutomaticFloor = automatic == null || automatic.getLong("sample_floor") == null
                        ? 0 : automatic.getLong("sample_floor");
                if (currentAutomaticFloor != expectedAutomaticFloor) {
                    session.rollback();
                    return null;
                }
                Long id = session.insertForGeneratedKey(job);
                session.commit();
                return id;
            } catch (SQLException | RuntimeException error) {
                try { session.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                if (error instanceof SQLException) throw (SQLException) error;
                throw (RuntimeException) error;
            } finally {
                session.close();
            }
        }
        @Override public void markRunning(Long id) throws SQLException { db().execute("UPDATE " + TABLE + " SET status = 'running', start_time = NOW() WHERE id = ? AND status = 'pending'", id); }
        @Override public void markCompleted(Long id, Long candidateId, String summary) throws SQLException { db().execute("UPDATE " + TABLE + " SET status = 'completed', candidate_prompt_version_id = ?, analysis_summary = ?, error_msg = NULL, finish_time = NOW() WHERE id = ? AND status = 'running'", candidateId, summary, id); }
        @Override public void markFailed(Long id, String error) throws SQLException { db().execute("UPDATE " + TABLE + " SET status = 'failed', error_msg = ?, finish_time = NOW() WHERE id = ? AND status IN ('pending','running')", error, id); }
        @Override public Entity findJob(Long id) throws SQLException { return id == null ? null : db().queryOne("SELECT * FROM " + TABLE + " WHERE id = ?", id); }
        @Override public Entity findActiveAutomaticJob() throws SQLException { return db().queryOne("SELECT * FROM " + TABLE + " WHERE trigger_type = 'automatic' AND status IN ('pending','generating','waiting_for_holdout','evaluating') ORDER BY id DESC LIMIT 1"); }
        @Override public List<Entity> listAutomaticJobs(int limit) throws SQLException { return new ArrayList<>(db().query("SELECT * FROM " + TABLE + " WHERE trigger_type = 'automatic' ORDER BY id DESC LIMIT ?", limit)); }
        @Override public long latestAutomaticSampleFloor() throws SQLException { Entity row = db().queryOne("SELECT COALESCE(MAX(sample_max_item_id), 0) AS sample_floor FROM " + TABLE + " WHERE trigger_type = 'automatic'"); return row == null || row.getLong("sample_floor") == null ? 0 : row.getLong("sample_floor"); }
        @Override public long latestRollbackSampleFloor() throws SQLException { Entity row = db().queryOne("SELECT COALESCE(sample_floor_item_id, 0) AS sample_floor FROM qj_wardrobe_prompt_version_audit WHERE action IN ('manual_rollback', 'auto_rollback') ORDER BY id DESC LIMIT 1"); return row == null || row.getLong("sample_floor") == null ? 0 : row.getLong("sample_floor"); }

        @Override
        public boolean releaseMonitorBlocksOptimization() throws SQLException {
            Entity latest = db().queryOne("SELECT a.action, m.id AS monitor_id, m.status"
                    + " FROM qj_wardrobe_prompt_version_audit a"
                    + " LEFT JOIN qj_wardrobe_prompt_release_monitor m ON m.publication_audit_id = a.id"
                    + " ORDER BY a.id DESC LIMIT 1");
            return latest != null && "auto_publish".equals(latest.getStr("action"))
                    && (latest.getLong("monitor_id") == null
                    || Arrays.asList("collecting", "evaluating").contains(latest.getStr("status")));
        }

        @Override
        public boolean transition(Long id, String from, String to, Entity values) throws SQLException {
            StringBuilder sql = new StringBuilder("UPDATE ").append(TABLE).append(" SET status = ?");
            List<Object> params = new ArrayList<>(); params.add(to);
            appendValue(sql, params, values, "heldout_sample_ids_json"); appendValue(sql, params, values, "decision_reason");
            appendValue(sql, params, values, "error_msg"); appendValue(sql, params, values, "finish_time");
            appendValue(sql, params, values, "sample_max_item_id");
            appendValue(sql, params, values, "baseline_metrics_json"); appendValue(sql, params, values, "candidate_metrics_json");
            sql.append(" WHERE id = ? AND status = ? AND trigger_type = 'automatic'"); params.add(id); params.add(from);
            return db().execute(sql.toString(), params.toArray()) == 1;
        }

        @Override
        public boolean attachCandidate(Long id, String from, Long candidateId, String summary) throws SQLException {
            return db().execute("UPDATE " + TABLE + " SET candidate_prompt_version_id = ?, analysis_summary = ?"
                    + " WHERE id = ? AND status = ? AND trigger_type = 'automatic'"
                    + " AND candidate_prompt_version_id IS NULL", candidateId, summary, id, from) == 1;
        }

        private StringBuilder eligibleSql(String where) {
            return new StringBuilder("SELECT id, review_time, sample_type, nav_item_cur, ai_evaluation_json, advisor_evaluation_json, diff_json FROM qj_wardrobe_item")
                    .append(where).append(" AND status = 'completed' AND sample_type IN ('positive','negative')")
                    .append(" AND review_time IS NOT NULL AND ai_evaluation_json IS NOT NULL AND advisor_evaluation_json IS NOT NULL")
                    .append(" AND JSON_VALID(ai_evaluation_json) AND JSON_VALID(advisor_evaluation_json)");
        }

        private void appendManualFilter(StringBuilder sql, List<Object> params, JSONObject filter) {
            Integer navItemCur = filter.getInt("navItemCur");
            if (navItemCur != null) { if (navItemCur != 1 && navItemCur != 2) throw new IllegalArgumentException("navItemCur must be 1 or 2"); sql.append(" AND nav_item_cur = ?"); params.add(navItemCur); }
            Long promptVersionId = filter.getLong("promptVersionId"); if (promptVersionId != null) { sql.append(" AND prompt_version_id = ?"); params.add(promptVersionId); }
            Date from = filter.getDate("from"); if (from != null) { sql.append(" AND review_time >= ?"); params.add(from); }
            Date to = filter.getDate("to"); if (to != null) { sql.append(" AND review_time < ?"); params.add(to); }
        }

        private void appendValue(StringBuilder sql, List<Object> params, Entity values, String column) {
            if (values != null && values.containsKey(column)) { sql.append(", ").append(column).append(" = ?"); params.add(values.get(column)); }
        }

        private Session newSession() {
            String key = DBConfig.getDbSetting().getStr("db_env") + "_qj";
            return Session.create(DSFactory.get(key));
        }
    }
}
