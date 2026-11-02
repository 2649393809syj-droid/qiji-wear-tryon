package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.db.DBConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Resolves, versions, publishes, and rolls back wardrobe recognition prompts. */
@Service
public class WardrobePromptVersionService {

    private static final String TABLE = "qj_wardrobe_prompt_version";
    private final Store store;

    @Autowired
    public WardrobePromptVersionService() {
        this(new HutoolStore());
    }

    WardrobePromptVersionService(Store store) {
        this.store = store;
    }

    public PromptVersion getActivePrompt() throws SQLException {
        Entity active = store.findActive();
        if (active == null) {
            throw new IllegalStateException("No active wardrobe Prompt version");
        }
        return toVersion(active);
    }

    public PromptVersion get(Long id) throws SQLException {
        Entity row = store.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("Prompt version does not exist: " + id);
        }
        return toVersion(row);
    }

    public PromptVersion findByVersionName(String versionName) throws SQLException {
        if (StrUtil.isBlank(versionName)) {
            return null;
        }
        Entity row = store.findByVersionName(versionName.trim());
        return row == null ? null : toVersion(row);
    }

    public JSONArray listJson() throws SQLException {
        JSONArray rows = new JSONArray();
        for (Entity row : store.list()) {
            rows.add(toVersion(row).toJson());
        }
        return rows;
    }

    public PromptVersion createCandidate(String versionName, String promptText, Long parentVersionId,
                                         String source, String summary, String createdBy) throws SQLException {
        if (StrUtil.isBlank(versionName) || StrUtil.isBlank(promptText)) {
            throw new IllegalArgumentException("versionName and promptText are required");
        }
        if (parentVersionId != null && store.findById(parentVersionId) == null) {
            throw new IllegalArgumentException("Parent Prompt version does not exist: " + parentVersionId);
        }
        String normalizedSource = StrUtil.isBlank(source) ? "manual" : source.trim();
        if (!"manual".equals(normalizedSource) && !"ai_generated".equals(normalizedSource)) {
            throw new IllegalArgumentException("Candidate source must be manual or ai_generated");
        }
        Long id = store.insertCandidate(Entity.create(TABLE)
                .set("version_name", versionName.trim())
                .set("prompt_text", promptText.trim())
                .set("status", "candidate")
                .set("source", normalizedSource)
                .set("parent_version_id", parentVersionId)
                .set("summary", StrUtil.trim(summary))
                .set("created_by", StrUtil.trim(createdBy))
                .set("create_time", new Date()));
        return get(id);
    }

    /** @deprecated Prompt publication is performed by the automatic optimizer. */
    @Deprecated
    public PromptVersion publish(Long id, String publisher) {
        throw new UnsupportedOperationException("Prompt publication is handled automatically");
    }

    /** @deprecated Advisor rollback requires a reason. */
    @Deprecated
    public PromptVersion rollback(Long id, String publisher) {
        throw new UnsupportedOperationException("Prompt rollback requires an advisor reason");
    }

    /** @deprecated Evaluated candidates now use the evaluating state. */
    @Deprecated
    public PromptVersion markReadyForReview(Long id) throws SQLException {
        return markEvaluating(id);
    }

    public PromptVersion markEvaluating(Long candidateId) throws SQLException {
        PromptVersion version = get(candidateId);
        if ("evaluating".equals(version.getStatus())) {
            return version;
        }
        if (!"candidate".equals(version.getStatus())) {
            throw new IllegalStateException("Only a candidate Prompt can become evaluating");
        }
        store.changeStatus(candidateId, "candidate", "evaluating", null);
        return get(candidateId);
    }

    public PromptVersion autoPublish(Long candidateId, Long expectedBaseVersionId, Long optimizationId,
                                     String reason) throws SQLException {
        PromptVersion version = get(candidateId);
        if (!"evaluating".equals(version.getStatus())) {
            throw new IllegalStateException("Only an evaluating Prompt can be automatically published");
        }
        if (expectedBaseVersionId == null) {
            throw new IllegalArgumentException("expectedBaseVersionId is required");
        }
        if (optimizationId == null) {
            throw new IllegalArgumentException("optimizationId is required");
        }
        if (StrUtil.isBlank(reason)) {
            throw new IllegalArgumentException("reason is required");
        }
        store.activate(new VersionSwitch(candidateId, expectedBaseVersionId, "auto_publish", "system",
                "system:auto-optimizer", reason.trim(), optimizationId, null));
        return get(candidateId);
    }

    public PromptVersion reject(Long candidateId, String reason) throws SQLException {
        if (StrUtil.isBlank(reason)) {
            throw new IllegalArgumentException("reason is required");
        }
        PromptVersion version = get(candidateId);
        if ("rejected".equals(version.getStatus())
                && StrUtil.trim(reason).equals(version.getRejectionReason())) {
            return version;
        }
        if (!"candidate".equals(version.getStatus()) && !"evaluating".equals(version.getStatus())) {
            throw new IllegalStateException("Only a candidate or evaluating Prompt can be rejected");
        }
        store.changeStatus(candidateId, version.getStatus(), "rejected", StrUtil.trim(reason));
        return get(candidateId);
    }

    public PromptVersion rollback(Long retiredId, String advisorOpenid, String reason) throws SQLException {
        if (StrUtil.isBlank(advisorOpenid)) {
            throw new IllegalArgumentException("advisorOpenid is required");
        }
        if (StrUtil.isBlank(reason)) {
            throw new IllegalArgumentException("reason is required");
        }
        PromptVersion version = get(retiredId);
        if (!"retired".equals(version.getStatus())) {
            throw new IllegalStateException("Only a retired Prompt version can be restored");
        }
        store.activate(new VersionSwitch(retiredId, null, "manual_rollback", "advisor",
                advisorOpenid.trim(), reason.trim(), null, null));
        return get(retiredId);
    }

    public PromptVersion autoRollback(Long retiredId, Long expectedActiveId, Long monitorId,
                                      Long sampleFloorItemId, String reason) throws SQLException {
        if (expectedActiveId == null || monitorId == null || sampleFloorItemId == null) {
            throw new IllegalArgumentException("expectedActiveId, monitorId and sampleFloorItemId are required");
        }
        if (sampleFloorItemId < 0 || StrUtil.isBlank(reason)) {
            throw new IllegalArgumentException("A valid sample floor and reason are required");
        }
        PromptVersion active = getActivePrompt();
        if (!expectedActiveId.equals(active.getId())) {
            throw new IllegalStateException("Prompt active version changed during release monitoring");
        }
        PromptVersion target = get(retiredId);
        if (!"retired".equals(target.getStatus()) || !retiredId.equals(active.getParentVersionId())) {
            throw new IllegalStateException("Only the active Prompt's direct predecessor can be restored automatically");
        }
        store.activate(new VersionSwitch(retiredId, expectedActiveId, "auto_rollback", "system",
                "system:auto-monitor", reason.trim(), null, sampleFloorItemId, monitorId));
        return get(retiredId);
    }

    private PromptVersion toVersion(Entity row) {
        return new PromptVersion(
                row.getLong("id"), row.getStr("version_name"), row.getStr("prompt_text"),
                row.getStr("status"), row.getStr("source"), row.getLong("parent_version_id"),
                row.getStr("summary"), row.getStr("created_by"), row.getDate("create_time"),
                row.getStr("published_by"), row.getDate("publish_time"), row.getDate("retired_time"),
                row.getStr("rejection_reason"));
    }

    public interface Store {
        Entity findActive() throws SQLException;
        Entity findById(Long id) throws SQLException;
        Entity findByVersionName(String versionName) throws SQLException;
        List<Entity> list() throws SQLException;
        Long insertCandidate(Entity candidate) throws SQLException;
        void activate(VersionSwitch change) throws SQLException;
        void changeStatus(Long id, String expectedStatus, String targetStatus, String reason) throws SQLException;
    }

    interface TransactionSessionFactory {
        TransactionSession open();
    }

    interface TransactionSession {
        void beginTransaction() throws SQLException;
        List<Entity> query(String sql, Object... params) throws SQLException;
        Entity queryOne(String sql, Object... params) throws SQLException;
        int execute(String sql, Object... params) throws SQLException;
        void commit() throws SQLException;
        void rollback() throws SQLException;
        void close();
    }

    static final class HutoolStore implements Store {
        private final TransactionSessionFactory transactionSessions;

        HutoolStore() {
            this(HutoolStore::newTransactionSession);
        }

        HutoolStore(TransactionSessionFactory transactionSessions) {
            this.transactionSessions = transactionSessions;
        }

        private Db db() {
            return DBConfig.getDb(null, "qj");
        }

        @Override
        public Entity findActive() throws SQLException {
            List<Entity> active = db().query("SELECT * FROM " + TABLE
                    + " WHERE status = 'active' ORDER BY id DESC LIMIT 2");
            if (active.size() > 1) {
                throw new IllegalStateException("Multiple active wardrobe Prompt versions");
            }
            return active.isEmpty() ? null : active.get(0);
        }

        @Override
        public Entity findById(Long id) throws SQLException {
            return id == null ? null : db().queryOne("SELECT * FROM " + TABLE + " WHERE id = ?", id);
        }

        @Override
        public Entity findByVersionName(String versionName) throws SQLException {
            return StrUtil.isBlank(versionName) ? null : db().queryOne("SELECT * FROM " + TABLE
                    + " WHERE version_name = ? ORDER BY id DESC LIMIT 1", versionName.trim());
        }

        @Override
        public List<Entity> list() throws SQLException {
            return new ArrayList<>(db().query("SELECT * FROM " + TABLE
                    + " ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'evaluating' THEN 1"
                    + " WHEN 'candidate' THEN 2 WHEN 'retired' THEN 3 ELSE 4 END, id DESC"));
        }

        @Override
        public Long insertCandidate(Entity candidate) throws SQLException {
            return db().insertForGeneratedKey(candidate);
        }

        @Override
        public void activate(VersionSwitch change) throws SQLException {
            TransactionSession session = transactionSessions.open();
            try {
                session.beginTransaction();
                List<Entity> activeRows = session.query("SELECT * FROM " + TABLE
                        + " WHERE status = 'active' FOR UPDATE");
                if (activeRows.size() != 1) {
                    throw new IllegalStateException("Expected exactly one active wardrobe Prompt version");
                }
                Entity active = activeRows.get(0);
                if (change.expectedActiveId != null && !change.expectedActiveId.equals(active.getLong("id"))) {
                    throw new IllegalStateException("Prompt active version changed during evaluation");
                }
                Entity target = session.queryOne("SELECT * FROM " + TABLE + " WHERE id = ? FOR UPDATE",
                        change.targetId);
                if (target == null) {
                    throw new IllegalArgumentException("Prompt version does not exist: " + change.targetId);
                }
                String expectedTargetStatus = "auto_publish".equals(change.action) ? "evaluating" : "retired";
                if (!expectedTargetStatus.equals(target.getStr("status"))) {
                    throw new IllegalStateException("Prompt version is no longer eligible for " + change.action);
                }
                if ("auto_publish".equals(change.action)) {
                    validateAutomaticTarget(change, target);
                    validateAutomaticOptimization(change, session.queryOne(
                            "SELECT * FROM qj_wardrobe_prompt_optimization WHERE id = ? FOR UPDATE",
                            change.optimizationId));
                }

                Entity monitor = null;
                if ("auto_rollback".equals(change.action)) {
                    if (!change.targetId.equals(active.getLong("parent_version_id"))) {
                        throw new IllegalStateException("Automatic rollback target is not the direct predecessor");
                    }
                    monitor = session.queryOne("SELECT * FROM qj_wardrobe_prompt_release_monitor"
                            + " WHERE id = ? FOR UPDATE", change.monitorId);
                    if (monitor == null || !"evaluating".equals(monitor.getStr("status"))
                            || !active.getLong("id").equals(monitor.getLong("published_prompt_version_id"))
                            || !change.targetId.equals(monitor.getLong("baseline_prompt_version_id"))) {
                        throw new IllegalStateException("Prompt release monitor does not match the automatic rollback");
                    }
                }

                Long sampleFloorItemId = change.sampleFloorItemId;
                if ("auto_publish".equals(change.action)) {
                    Entity floor = session.queryOne("SELECT COALESCE(MAX(id), 0) AS sample_floor_item_id"
                            + " FROM qj_wardrobe_item FOR UPDATE");
                    sampleFloorItemId = floor.getLong("sample_floor_item_id");
                }
                if ("manual_rollback".equals(change.action)) {
                    Entity floor = session.queryOne("SELECT COALESCE(MAX(id), 0) AS sample_floor_item_id"
                            + " FROM qj_wardrobe_item WHERE status = 'completed' FOR UPDATE");
                    sampleFloorItemId = floor.getLong("sample_floor_item_id");
                }
                if ("manual_rollback".equals(change.action) || "auto_rollback".equals(change.action)) {
                    session.execute("UPDATE qj_wardrobe_prompt_optimization SET status = 'stale', finish_time = NOW()"
                                    + " WHERE trigger_type = 'automatic' AND base_prompt_version_id = ?"
                                    + " AND status IN ('pending','waiting_for_holdout','generating','evaluating')",
                            active.getLong("id"));
                }
                if ("auto_publish".equals(change.action)) {
                    requireOne(session.execute("UPDATE qj_wardrobe_prompt_optimization"
                            + " SET status = 'published', publish_time = NOW()"
                            + " WHERE id = ? AND status = 'evaluating' AND trigger_type = 'automatic'"
                            + " AND base_prompt_version_id = ? AND candidate_prompt_version_id = ?",
                            change.optimizationId, change.expectedActiveId, change.targetId),
                            "Prompt optimization is no longer eligible for publication");
                }
                if ("auto_rollback".equals(change.action)) {
                    requireOne(session.execute("UPDATE qj_wardrobe_prompt_release_monitor"
                                    + " SET status = 'rolled_back', decision_reason = ?, sample_max_item_id = ?,"
                                    + " finish_time = NOW(), update_time = NOW()"
                                    + " WHERE id = ? AND status = 'evaluating'"
                                    + " AND published_prompt_version_id = ? AND baseline_prompt_version_id = ?",
                            change.reason, sampleFloorItemId, change.monitorId,
                            active.getLong("id"), change.targetId),
                            "Prompt release monitor is no longer eligible for rollback");
                }
                requireOne(session.execute("UPDATE " + TABLE
                        + " SET status = 'retired', retired_time = NOW() WHERE id = ? AND status = 'active'",
                        active.getLong("id")), "Active Prompt version changed during switch");
                requireOne(session.execute("UPDATE " + TABLE
                                + " SET status = 'active', published_by = ?, publish_time = NOW(), retired_time = NULL"
                                + " WHERE id = ? AND status = ?",
                        change.actorId, change.targetId, expectedTargetStatus),
                        "Target Prompt version changed during switch");
                requireOne(session.execute("INSERT INTO qj_wardrobe_prompt_version_audit"
                                + " (action, from_version_id, to_version_id, actor_type, actor_id, reason,"
                                + " optimization_id, sample_floor_item_id, create_time)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                        change.action, active.getLong("id"), change.targetId, change.actorType, change.actorId,
                        change.reason, monitor == null ? change.optimizationId : monitor.getLong("optimization_id"),
                        sampleFloorItemId),
                        "Prompt version audit was not inserted");
                session.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackAfterFailure(session, e);
                throw e;
            } finally {
                session.close();
            }
        }

        @Override
        public void changeStatus(Long id, String expectedStatus, String targetStatus, String reason) throws SQLException {
            TransactionSession session = transactionSessions.open();
            try {
                session.beginTransaction();
                int changed = reason == null
                        ? session.execute("UPDATE " + TABLE + " SET status = ? WHERE id = ? AND status = ?",
                        targetStatus, id, expectedStatus)
                        : session.execute("UPDATE " + TABLE
                                + " SET status = ?, rejection_reason = ? WHERE id = ? AND status = ?",
                        targetStatus, reason, id, expectedStatus);
                requireOne(changed, "Prompt version is no longer eligible for status change");
                session.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackAfterFailure(session, e);
                throw e;
            } finally {
                session.close();
            }
        }

        private void validateAutomaticTarget(VersionSwitch change, Entity target) {
            if (!"ai_generated".equals(target.getStr("source"))
                    || !change.expectedActiveId.equals(target.getLong("parent_version_id"))) {
                throw new IllegalStateException("Automatic Prompt candidate does not match its evaluated base");
            }
        }

        private void validateAutomaticOptimization(VersionSwitch change, Entity optimization) {
            if (optimization == null || !change.optimizationId.equals(optimization.getLong("id"))
                    || !"evaluating".equals(optimization.getStr("status"))
                    || !"automatic".equals(optimization.getStr("trigger_type"))
                    || !change.expectedActiveId.equals(optimization.getLong("base_prompt_version_id"))
                    || !change.targetId.equals(optimization.getLong("candidate_prompt_version_id"))) {
                throw new IllegalStateException("Prompt optimization does not match the automatic publication");
            }
        }

        private void requireOne(int changed, String message) {
            if (changed != 1) {
                throw new IllegalStateException(message);
            }
        }

        private void rollbackAfterFailure(TransactionSession session, Exception failure) {
            try {
                session.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }

        private static TransactionSession newTransactionSession() {
            return new HutoolTransactionSession(newSession());
        }

        private static Session newSession() {
            String key = DBConfig.getDbSetting().getStr("db_env") + "_qj";
            return Session.create(DSFactory.get(key));
        }
    }

    private static final class HutoolTransactionSession implements TransactionSession {
        private final Session session;

        private HutoolTransactionSession(Session session) {
            this.session = session;
        }

        @Override
        public void beginTransaction() throws SQLException {
            session.beginTransaction();
        }

        @Override
        public List<Entity> query(String sql, Object... params) throws SQLException {
            return session.query(sql, params);
        }

        @Override
        public Entity queryOne(String sql, Object... params) throws SQLException {
            return session.queryOne(sql, params);
        }

        @Override
        public int execute(String sql, Object... params) throws SQLException {
            return session.execute(sql, params);
        }

        @Override
        public void commit() throws SQLException {
            session.commit();
        }

        @Override
        public void rollback() throws SQLException {
            session.rollback();
        }

        @Override
        public void close() {
            session.close();
        }
    }

    public static final class VersionSwitch {
        final Long targetId;
        final Long expectedActiveId;
        final String action;
        final String actorType;
        final String actorId;
        final String reason;
        final Long optimizationId;
        final Long sampleFloorItemId;
        final Long monitorId;

        VersionSwitch(Long targetId, Long expectedActiveId, String action, String actorType, String actorId,
                      String reason, Long optimizationId, Long sampleFloorItemId) {
            this(targetId, expectedActiveId, action, actorType, actorId, reason, optimizationId,
                    sampleFloorItemId, null);
        }

        VersionSwitch(Long targetId, Long expectedActiveId, String action, String actorType, String actorId,
                      String reason, Long optimizationId, Long sampleFloorItemId, Long monitorId) {
            this.targetId = targetId;
            this.expectedActiveId = expectedActiveId;
            this.action = action;
            this.actorType = actorType;
            this.actorId = actorId;
            this.reason = reason;
            this.optimizationId = optimizationId;
            this.sampleFloorItemId = sampleFloorItemId;
            this.monitorId = monitorId;
        }
    }

    public static final class PromptVersion {
        private final Long id;
        private final String versionName;
        private final String promptText;
        private final String status;
        private final String source;
        private final Long parentVersionId;
        private final String summary;
        private final String createdBy;
        private final Date createTime;
        private final String publishedBy;
        private final Date publishTime;
        private final Date retiredTime;
        private final String rejectionReason;

        public PromptVersion(Long id, String versionName, String promptText, String status, String source,
                             Long parentVersionId, String summary, String createdBy, Date createTime,
                             String publishedBy, Date publishTime, Date retiredTime) {
            this(id, versionName, promptText, status, source, parentVersionId, summary, createdBy, createTime,
                    publishedBy, publishTime, retiredTime, null);
        }

        public PromptVersion(Long id, String versionName, String promptText, String status, String source,
                             Long parentVersionId, String summary, String createdBy, Date createTime,
                             String publishedBy, Date publishTime, Date retiredTime, String rejectionReason) {
            this.id = id;
            this.versionName = versionName;
            this.promptText = promptText;
            this.status = status;
            this.source = source;
            this.parentVersionId = parentVersionId;
            this.summary = summary;
            this.createdBy = createdBy;
            this.createTime = createTime;
            this.publishedBy = publishedBy;
            this.publishTime = publishTime;
            this.retiredTime = retiredTime;
            this.rejectionReason = rejectionReason;
        }

        public Long getId() { return id; }
        public String getVersionName() { return versionName; }
        public String getPromptText() { return promptText; }
        public String getStatus() { return status; }
        public String getSource() { return source; }
        public Long getParentVersionId() { return parentVersionId; }
        public String getSummary() { return summary; }
        public String getRejectionReason() { return rejectionReason; }

        public JSONObject toJson() {
            return new JSONObject()
                    .set("id", id).set("versionName", versionName).set("promptText", promptText)
                    .set("status", status).set("source", source).set("parentVersionId", parentVersionId)
                    .set("summary", summary).set("createdBy", createdBy).set("createTime", createTime)
                    .set("publishedBy", publishedBy).set("publishTime", publishTime).set("retiredTime", retiredTime)
                    .set("rejectionReason", rejectionReason);
        }
    }
}
