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
import com.qj.entities.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Single-pass AI recognition and advisor completion for legacy wardrobe items. */
@Service
public class WardrobeService {

    private static final Logger log = LoggerFactory.getLogger(WardrobeService.class);
    private static final String ITEM_TABLE = "qj_wardrobe_item";
    private static final String HISTORY_TABLE = "qj_wardrobe_style_history";
    private static final String FLOW_VERSION = "single_pass_v2";
    private static final long STALE_PENDING_MILLIS = 15 * 60 * 1000L;
    private static final Set<Long> ACTIVE_RECOGNITIONS = ConcurrentHashMap.newKeySet();

    private final GeminiStyleRecognitionService gemini;
    private final WardrobeEvaluationNormalizer normalizer;
    private final Store store;
    private final PromptProvider promptProvider;
    private final ApplicationEventPublisher events;

    @Autowired
    public WardrobeService(GeminiStyleRecognitionService gemini,
                           WardrobePromptVersionService promptVersionService,
                           ApplicationEventPublisher events) {
        this(gemini, new HutoolStore(), promptVersionService::getActivePrompt, events);
    }

    WardrobeService(GeminiStyleRecognitionService gemini, Store store) {
        this(gemini, store, () -> new WardrobePromptVersionService.PromptVersion(
                1L, "wardrobe-style-legacy-v1", gemini.getPromptText(), "active", "seed",
                null, null, null, null, null, null, null), event -> { });
    }

    WardrobeService(GeminiStyleRecognitionService gemini, Store store, PromptProvider promptProvider) {
        this(gemini, store, promptProvider, event -> { });
    }

    WardrobeService(GeminiStyleRecognitionService gemini, Store store, PromptProvider promptProvider,
                    ApplicationEventPublisher events) {
        this.gemini = gemini;
        this.normalizer = new WardrobeEvaluationNormalizer();
        this.store = store;
        this.promptProvider = promptProvider;
        this.events = events;
    }

    public AgentResponse createItem(Long autoId, String openid, Integer navItemCur, String imageUrl) {
        String validation = validateIdentity(autoId, openid, navItemCur, imageUrl);
        if (validation != null) {
            return AgentResponse.fail(validation);
        }
        try {
            Entity existing = store.findByLegacy(autoId, navItemCur);
            if (existing != null) {
                return success(recoverInterruptedRecognition(existing));
            }
            WardrobePromptVersionService.PromptVersion prompt = promptProvider.getActivePrompt();
            Entity item = Entity.create(ITEM_TABLE)
                    .set("legacy_auto_id", autoId)
                    .set("nav_item_cur", navItemCur)
                    .set("openid", openid.trim())
                    .set("image_url", imageUrl.trim())
                    .set("status", "pending_ai")
                    .set("prompt_version_id", prompt.getId())
                    .set("flow_version", FLOW_VERSION)
                    .set("create_time", new Date())
                    .set("update_time", new Date());
            Long itemId;
            try {
                itemId = store.insertItem(item);
            } catch (SQLException duplicate) {
                Entity concurrentlyCreated = store.findByLegacy(autoId, navItemCur);
                if (concurrentlyCreated == null) {
                    throw duplicate;
                }
                return success(concurrentlyCreated);
            }
            recognizeOnce(itemId, prompt);
            return getItem(itemId);
        } catch (Exception e) {
            log.error("Create single-pass wardrobe item failed autoId={} navItemCur={}", autoId, navItemCur, e);
            return AgentResponse.fail("Create wardrobe AI record failed: " + safeMessage(e));
        }
    }

    public AgentResponse getByLegacy(Long autoId, Integer navItemCur) {
        if (autoId == null || autoId <= 0 || !validNav(navItemCur)) {
            return AgentResponse.fail("autoId and navItemCur (1 clothing or 2 ornament) are required");
        }
        try {
            Entity item = store.findByLegacy(autoId, navItemCur);
            return item == null ? new AgentResponse(404, "Wardrobe AI record does not exist", null)
                    : success(recoverInterruptedRecognition(item));
        } catch (SQLException e) {
            log.error("Query wardrobe item by legacy identity failed autoId={} navItemCur={}", autoId, navItemCur, e);
            return AgentResponse.fail("Query wardrobe AI record failed: " + safeMessage(e));
        }
    }

    public AgentResponse complete(Long itemId, String reviewerOpenid, JSONObject advisorEvaluationJson) {
        if (itemId == null || StrUtil.isBlank(reviewerOpenid) || advisorEvaluationJson == null) {
            return AgentResponse.fail("itemId, reviewerOpenid and advisorEvaluationJson are required");
        }
        try {
            Entity item = requireItem(itemId);
            if ("completed".equals(item.getStr("status"))) {
                return success(item);
            }
            Long autoId = item.getLong("legacy_auto_id");
            Integer navItemCur = item.getInt("nav_item_cur");
            JSONObject advisor = normalizer.normalize(advisorEvaluationJson, autoId, navItemCur);
            String sampleType = null;
            JSONObject diff = null;
            String aiJson = item.getStr("ai_evaluation_json");
            if (StrUtil.isNotBlank(aiJson)) {
                WardrobeEvaluationNormalizer.Comparison comparison = normalizer.compare(
                        JSONUtil.parseObj(aiJson), advisor, autoId, navItemCur);
                advisor = comparison.getAdvisorEvaluation();
                sampleType = comparison.getSampleType();
                diff = comparison.getDiffJson();
            } else if (!"ai_failed".equals(item.getStr("status"))) {
                return new AgentResponse(409, "AI recognition is still processing", null);
            }
            boolean completed = store.complete(itemId, advisor, sampleType, diff, reviewerOpenid.trim());
            if (completed && sampleType != null) {
                try {
                    events.publishEvent(new WardrobeReviewCompletedEvent(itemId));
                } catch (RuntimeException eventError) {
                    log.warn("Wardrobe optimization wakeup event failed itemId={}; scheduled recovery will retry",
                            itemId, eventError);
                }
            }
            return getItem(itemId);
        } catch (IllegalArgumentException e) {
            return AgentResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("Complete wardrobe review failed itemId={}", itemId, e);
            return AgentResponse.fail("Complete wardrobe review failed: " + safeMessage(e));
        }
    }

    public AgentResponse getUserItems(String openid, String status, Integer pageNumber, Integer pageSize) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("openid is required");
        }
        try {
            int page = validPage(pageNumber);
            int size = validSize(pageSize);
            JSONArray rows = new JSONArray();
            for (Entity item : store.findUserItems(openid, status, size, (page - 1) * size)) {
                rows.add(toItemJson(item));
            }
            return AgentResponse.success("Query succeeded", new JSONObject()
                    .set("list", rows)
                    .set("total", store.countUserItems(openid, status))
                    .set("pageNumber", page)
                    .set("pageSize", size));
        } catch (SQLException e) {
            return AgentResponse.fail("Query wardrobe failed: " + safeMessage(e));
        }
    }

    public AgentResponse getPendingReviews(Integer pageNumber, Integer pageSize) {
        try {
            int page = validPage(pageNumber);
            int size = validSize(pageSize);
            JSONArray rows = new JSONArray();
            for (Entity item : store.findPendingItems(size, (page - 1) * size)) {
                rows.add(toItemJson(item));
            }
            return AgentResponse.success("Query succeeded", new JSONObject()
                    .set("list", rows)
                    .set("total", store.countPendingItems())
                    .set("pageNumber", page)
                    .set("pageSize", size));
        } catch (SQLException e) {
            return AgentResponse.fail("Query review list failed: " + safeMessage(e));
        }
    }

    public AgentResponse getHistory(Long itemId) {
        try {
            Entity item = requireItem(itemId);
            JSONArray rows = new JSONArray();
            for (Entity history : store.findHistory(itemId)) {
                JSONObject row = toHistoryJson(history);
                if (FLOW_VERSION.equals(history.getStr("flow_version"))) {
                    row.set("sampleType", item.getStr("sample_type"))
                            .set("diffJson", parseObj(item.getStr("diff_json")));
                    if (row.getJSONObject("aiEvaluationJson") == null) {
                        row.set("aiEvaluationJson", parseObj(item.getStr("ai_evaluation_json")));
                    }
                    if (row.getJSONObject("advisorEvaluationJson") == null) {
                        row.set("advisorEvaluationJson", parseObj(item.getStr("advisor_evaluation_json")));
                    }
                }
                rows.add(row);
            }
            return AgentResponse.success("Query succeeded", rows);
        } catch (SQLException e) {
            return AgentResponse.fail("Query history failed: " + safeMessage(e));
        }
    }

    public AgentResponse health() {
        try {
            Map<String, Long> counts = store.tableCounts();
            return AgentResponse.success("Wardrobe AI service is ready", new JSONObject()
                    .set("tables", counts)
                    .set("geminiConfigured", gemini.isConfigured())
                    .set("geminiModel", gemini.getModel())
                    .set("promptVersion", gemini.getPromptVersion())
                    .set("flowVersion", FLOW_VERSION));
        } catch (SQLException e) {
            return new AgentResponse(500, "Wardrobe AI service needs database migration", safeMessage(e));
        }
    }

    private void recognizeOnce(Long itemId, WardrobePromptVersionService.PromptVersion prompt) {
        ACTIVE_RECOGNITIONS.add(itemId);
        try {
            Entity item = requireItem(itemId);
            GeminiStyleRecognitionService.GeminiRecognitionResult result =
                    gemini.recognize(item.getStr("image_url"), prompt.getPromptText());
            JSONObject evaluation = normalizer.normalize(result.getEvaluationJson(),
                    item.getLong("legacy_auto_id"), item.getInt("nav_item_cur"));
            Entity history = Entity.create(HISTORY_TABLE)
                    .set("wardrobe_item_id", itemId)
                    .set("round_no", 1)
                    .set("prompt_version", prompt.getVersionName())
                    .set("prompt_text", result.getPromptText())
                    .set("model_name", result.getModelName())
                    .set("ai_style_json", evaluation.toString())
                    .set("review_status", "ai_generated")
                    .set("gemini_raw_response", result.getRawResponse())
                    .set("flow_version", FLOW_VERSION)
                    .set("create_time", new Date());
            store.persistAiSuccess(itemId, history, evaluation);
        } catch (Exception e) {
            try {
                GeminiStyleRecognitionService.GeminiRecognitionException recognitionFailure =
                        e instanceof GeminiStyleRecognitionService.GeminiRecognitionException
                                ? (GeminiStyleRecognitionService.GeminiRecognitionException) e : null;
                Entity history = Entity.create(HISTORY_TABLE)
                        .set("wardrobe_item_id", itemId)
                        .set("round_no", 1)
                        .set("prompt_version", prompt.getVersionName())
                        .set("prompt_text", recognitionFailure == null
                                ? prompt.getPromptText() : recognitionFailure.getPromptText())
                        .set("model_name", recognitionFailure == null
                                ? gemini.getModel() : recognitionFailure.getAttemptedModels())
                        .set("gemini_raw_response", recognitionFailure == null
                                ? null : recognitionFailure.getRawResponse())
                        .set("review_status", "ai_failed")
                        .set("error_msg", safeMessage(e))
                        .set("flow_version", FLOW_VERSION)
                        .set("create_time", new Date());
                store.persistAiFailure(itemId, history, safeMessage(e));
            } catch (Exception persistenceError) {
                log.error("Persist wardrobe AI failure failed itemId={}", itemId, persistenceError);
            }
            log.warn("Wardrobe AI recognition failed itemId={}: {}", itemId, safeMessage(e));
        } finally {
            ACTIVE_RECOGNITIONS.remove(itemId);
        }
    }

    private Entity recoverInterruptedRecognition(Entity item) throws SQLException {
        if (!"pending_ai".equals(item.getStr("status"))) {
            return item;
        }
        if (ACTIVE_RECOGNITIONS.contains(item.getLong("id"))) {
            return item;
        }
        Date created = item.getDate("create_time");
        if (created == null || System.currentTimeMillis() - created.getTime() < STALE_PENDING_MILLIS) {
            return item;
        }
        String error = "AI recognition was interrupted before a result was saved";
        Entity history = Entity.create(HISTORY_TABLE)
                .set("wardrobe_item_id", item.getLong("id"))
                .set("round_no", 1)
                .set("prompt_version", gemini.getPromptVersion())
                .set("prompt_text", gemini.getPromptText())
                .set("model_name", gemini.getModel())
                .set("review_status", "ai_failed")
                .set("error_msg", error)
                .set("flow_version", FLOW_VERSION)
                .set("create_time", new Date());
        store.persistAiFailure(item.getLong("id"), history, error);
        return requireItem(item.getLong("id"));
    }

    private AgentResponse getItem(Long itemId) throws SQLException {
        return success(requireItem(itemId));
    }

    private AgentResponse success(Entity item) throws SQLException {
        JSONObject data = toItemJson(item);
        Entity latest = store.findLatestHistory(item.getLong("id"));
        if (latest != null) {
            data.set("latestHistory", toHistoryJson(latest));
        }
        return AgentResponse.success("Operation succeeded", data);
    }

    private Entity requireItem(Long itemId) throws SQLException {
        Entity item = store.findItem(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Wardrobe AI record does not exist");
        }
        return item;
    }

    private JSONObject toItemJson(Entity item) {
        return new JSONObject()
                .set("id", item.getLong("id"))
                .set("autoId", item.getLong("legacy_auto_id"))
                .set("navItemCur", item.getInt("nav_item_cur"))
                .set("openid", item.getStr("openid"))
                .set("imageUrl", item.getStr("image_url"))
                .set("status", item.getStr("status"))
                .set("flowVersion", item.getStr("flow_version"))
                .set("promptVersionId", item.getLong("prompt_version_id"))
                .set("aiEvaluationJson", parseObj(item.getStr("ai_evaluation_json")))
                .set("advisorEvaluationJson", parseObj(item.getStr("advisor_evaluation_json")))
                .set("finalStyleJson", parseObj(item.getStr("final_style_json")))
                .set("sampleType", item.getStr("sample_type"))
                .set("diffJson", parseObj(item.getStr("diff_json")))
                .set("reviewerOpenid", item.getStr("reviewer_openid"))
                .set("errorMsg", item.getStr("error_msg"))
                .set("createTime", item.getDate("create_time"))
                .set("updateTime", item.getDate("update_time"))
                .set("reviewTime", item.getDate("review_time"));
    }

    private JSONObject toHistoryJson(Entity history) {
        return new JSONObject()
                .set("id", history.getLong("id"))
                .set("wardrobeItemId", history.getLong("wardrobe_item_id"))
                .set("roundNo", history.getInt("round_no"))
                .set("promptVersion", history.getStr("prompt_version"))
                .set("modelName", history.getStr("model_name"))
                .set("aiEvaluationJson", parseObj(history.getStr("ai_style_json")))
                .set("advisorEvaluationJson", parseObj(history.getStr("advisor_style_json")))
                .set("reviewStatus", history.getStr("review_status"))
                .set("reviewerOpenid", history.getStr("reviewer_openid"))
                .set("errorMsg", history.getStr("error_msg"))
                .set("flowVersion", history.getStr("flow_version"))
                .set("createTime", history.getDate("create_time"))
                .set("reviewTime", history.getDate("review_time"));
    }

    private JSONObject parseObj(String json) {
        return StrUtil.isBlank(json) ? null : JSONUtil.parseObj(json);
    }

    private String validateIdentity(Long autoId, String openid, Integer navItemCur, String imageUrl) {
        if (autoId == null || autoId <= 0) return "autoId is required";
        if (!validNav(navItemCur)) return "navItemCur must be 1 (clothing) or 2 (ornament)";
        if (StrUtil.isBlank(openid)) return "openid is required";
        if (StrUtil.isBlank(imageUrl)) return "imageUrl is required";
        return null;
    }

    private boolean validNav(Integer navItemCur) {
        return navItemCur != null && (navItemCur == 1 || navItemCur == 2);
    }

    private int validPage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int validSize(Integer size) {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (StrUtil.isBlank(message)) return "Unknown error";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public interface Store {
        Entity findByLegacy(Long autoId, Integer navItemCur) throws SQLException;
        Long insertItem(Entity item) throws SQLException;
        Entity findItem(Long itemId) throws SQLException;
        Entity findLatestHistory(Long itemId) throws SQLException;
        boolean persistAiSuccess(Long itemId, Entity history, JSONObject evaluation) throws SQLException;
        boolean persistAiFailure(Long itemId, Entity history, String error) throws SQLException;
        boolean complete(Long itemId, JSONObject advisorEvaluation, String sampleType,
                         JSONObject diff, String reviewerOpenid) throws SQLException;
        List<Entity> findUserItems(String openid, String status, int limit, int offset) throws SQLException;
        long countUserItems(String openid, String status) throws SQLException;
        List<Entity> findPendingItems(int limit, int offset) throws SQLException;
        long countPendingItems() throws SQLException;
        List<Entity> findHistory(Long itemId) throws SQLException;
        Map<String, Long> tableCounts() throws SQLException;
    }

    @FunctionalInterface
    interface PromptProvider {
        WardrobePromptVersionService.PromptVersion getActivePrompt() throws SQLException;
    }

    static final class HutoolStore implements Store {
        private Db db() {
            return DBConfig.getDb(null, "qj");
        }

        @Override
        public Entity findByLegacy(Long autoId, Integer navItemCur) throws SQLException {
            return db().queryOne("SELECT * FROM " + ITEM_TABLE
                    + " WHERE legacy_auto_id = ? AND nav_item_cur = ? LIMIT 1", autoId, navItemCur);
        }

        @Override
        public Long insertItem(Entity item) throws SQLException {
            return db().insertForGeneratedKey(item);
        }

        @Override
        public Entity findItem(Long itemId) throws SQLException {
            return db().queryOne("SELECT * FROM " + ITEM_TABLE + " WHERE id = ?", itemId);
        }

        @Override
        public Entity findLatestHistory(Long itemId) throws SQLException {
            return db().queryOne("SELECT * FROM " + HISTORY_TABLE
                    + " WHERE wardrobe_item_id = ? ORDER BY id DESC LIMIT 1", itemId);
        }

        @Override
        public boolean persistAiSuccess(Long itemId, Entity history, JSONObject evaluation) throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                int changed = session.execute("UPDATE " + ITEM_TABLE
                                + " SET ai_evaluation_json = ?, status = 'pending_review', update_time = NOW()"
                                + " WHERE id = ? AND status = 'pending_ai' AND ai_evaluation_json IS NULL",
                        evaluation.toString(), itemId);
                if (changed == 0) {
                    session.rollback();
                    return false;
                }
                session.insert(history);
                session.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                session.rollback();
                throw e;
            } finally {
                session.close();
            }
        }

        @Override
        public boolean persistAiFailure(Long itemId, Entity history, String error) throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                int changed = session.execute("UPDATE " + ITEM_TABLE
                                + " SET status = 'ai_failed', error_msg = ?, update_time = NOW()"
                                + " WHERE id = ? AND status = 'pending_ai' AND ai_evaluation_json IS NULL",
                        error, itemId);
                if (changed == 0) {
                    session.rollback();
                    return false;
                }
                session.insert(history);
                session.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                session.rollback();
                throw e;
            } finally {
                session.close();
            }
        }

        @Override
        public boolean complete(Long itemId, JSONObject advisorEvaluation, String sampleType,
                                JSONObject diff, String reviewerOpenid) throws SQLException {
            Session session = newSession();
            try {
                session.beginTransaction();
                int changed = session.execute("UPDATE " + ITEM_TABLE
                                + " SET advisor_evaluation_json = ?, final_style_json = ?, sample_type = ?, diff_json = ?,"
                                + " reviewer_openid = ?, review_time = NOW(), status = 'completed', update_time = NOW()"
                                + " WHERE id = ? AND status <> 'completed'",
                        advisorEvaluation.toString(), advisorEvaluation.toString(), sampleType,
                        diff == null ? null : diff.toString(), reviewerOpenid, itemId);
                if (changed == 0) {
                    session.rollback();
                    return false;
                }
                int historyChanged = session.execute("UPDATE " + HISTORY_TABLE
                                + " SET advisor_style_json = ?, reviewer_openid = ?, review_time = NOW(), review_status = 'completed'"
                                + " WHERE wardrobe_item_id = ? ORDER BY id DESC LIMIT 1",
                        advisorEvaluation.toString(), reviewerOpenid, itemId);
                if (historyChanged != 1) {
                    throw new SQLException("Single-pass history record is missing for item " + itemId);
                }
                session.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                session.rollback();
                throw e;
            } finally {
                session.close();
            }
        }

        private Session newSession() {
            String key = DBConfig.getDbSetting().getStr("db_env") + "_qj";
            return Session.create(DSFactory.get(key));
        }

        @Override
        public List<Entity> findUserItems(String openid, String status, int limit, int offset) throws SQLException {
            String sql = "SELECT * FROM " + ITEM_TABLE + " WHERE openid = ?"
                    + (StrUtil.isBlank(status) ? "" : " AND status = ?")
                    + " ORDER BY id DESC LIMIT ? OFFSET ?";
            return new ArrayList<>(StrUtil.isBlank(status)
                    ? db().query(sql, openid, limit, offset)
                    : db().query(sql, openid, status, limit, offset));
        }

        @Override
        public long countUserItems(String openid, String status) throws SQLException {
            Number total = StrUtil.isBlank(status)
                    ? db().queryNumber("SELECT COUNT(*) FROM " + ITEM_TABLE + " WHERE openid = ?", openid)
                    : db().queryNumber("SELECT COUNT(*) FROM " + ITEM_TABLE
                    + " WHERE openid = ? AND status = ?", openid, status);
            return total == null ? 0 : total.longValue();
        }

        @Override
        public List<Entity> findPendingItems(int limit, int offset) throws SQLException {
            return new ArrayList<>(db().query("SELECT * FROM " + ITEM_TABLE
                    + " WHERE status = 'pending_review' ORDER BY id DESC LIMIT ? OFFSET ?", limit, offset));
        }

        @Override
        public long countPendingItems() throws SQLException {
            Number total = db().queryNumber("SELECT COUNT(*) FROM " + ITEM_TABLE
                    + " WHERE status = 'pending_review'");
            return total == null ? 0 : total.longValue();
        }

        @Override
        public List<Entity> findHistory(Long itemId) throws SQLException {
            return new ArrayList<>(db().query("SELECT * FROM " + HISTORY_TABLE
                    + " WHERE wardrobe_item_id = ? ORDER BY round_no ASC, id ASC", itemId));
        }

        @Override
        public Map<String, Long> tableCounts() throws SQLException {
            Map<String, Long> result = new LinkedHashMap<>();
            for (String table : new String[]{ITEM_TABLE, HISTORY_TABLE}) {
                Number count = db().queryNumber("SELECT COUNT(*) FROM " + table);
                result.put(table, count == null ? 0 : count.longValue());
            }
            return result;
        }
    }
}
