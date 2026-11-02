package com.qj.services;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import com.qj.entities.AgentResponse;
import com.qj.entities.HistoryScheme;
import com.qj.utils.AccessTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方案服务类
 */
@Service
public class SchemeService {

    private static final Logger log = LoggerFactory.getLogger(SchemeService.class);

    /**
     * 获取数据库连接
     */
    private Db getDb() {
        return DBConfig.getDb(null, "qj");
    }

    /**
     * 添加方案（普通保存）
     */
    public AgentResponse addScheme(String openid, String collocationData, String humanData,
            Long schemeTime, Integer fromShopAdviser, String push,
            Integer shopId, String customerOpenid) {
        try {
            Entity entity = Entity.create("qj_history_scheme")
                    .set("openid", openid)
                    .set("collocation_data", collocationData)
                    .set("human_data", humanData)
                    .set("scheme_time", schemeTime)
                    .set("source_type", 1) // 自己生成
                    .set("from_shop_adviser", fromShopAdviser != null ? fromShopAdviser : 0)
                    .set("push", push)
                    .set("shop_id", shopId)
                    .set("customer_openid", customerOpenid)
                    .set("is_read", 1)
                    .set("status", 1)
                    .set("create_time", new Date())
                    .set("update_time", new Date());

            Long id = getDb().insertForGeneratedKey(entity);
            log.info("添加方案成功，ID: {}", id);

            JSONObject result = new JSONObject();
            result.set("schemeId", id);
            return AgentResponse.success("添加成功", result);
        } catch (SQLException e) {
            log.error("添加方案失败", e);
            return AgentResponse.fail("添加方案失败：" + e.getMessage());
        }
    }

    /**
     * 推送方案给指定用户（双向保存）
     */
    public AgentResponse pushSchemeToUser(String operatorOpenid, String targetOpenid,
            String collocationData, String humanData,
            Long schemeTime, Integer fromShopAdviser,
            Integer shopId) {
        try {
            // 1. 保存到目标用户（source_type=2，代理推送）
            Entity targetEntity = Entity.create("qj_history_scheme")
                    .set("openid", targetOpenid)
                    .set("operator_openid", operatorOpenid)
                    .set("collocation_data", collocationData)
                    .set("human_data", humanData)
                    .set("scheme_time", schemeTime)
                    .set("source_type", 2) // 代理推送
                    .set("from_shop_adviser", fromShopAdviser != null ? fromShopAdviser : 1)
                    .set("push", "1")
                    .set("shop_id", shopId)
                    .set("customer_openid", targetOpenid)
                    .set("is_read", 0) // 未读
                    .set("status", 1)
                    .set("create_time", new Date())
                    .set("update_time", new Date());

            Long targetSchemeId = getDb().insertForGeneratedKey(targetEntity);
            log.info("推送方案到目标用户成功，目标用户: {}, 方案ID: {}", targetOpenid, targetSchemeId);

            // 2. 保存到操作者（source_type=3，我推送给用户）
            Entity operatorEntity = Entity.create("qj_history_scheme")
                    .set("openid", operatorOpenid)
                    .set("operator_openid", operatorOpenid)
                    .set("collocation_data", collocationData)
                    .set("human_data", humanData)
                    .set("scheme_time", schemeTime)
                    .set("source_type", 3) // 我推送给用户
                    .set("from_shop_adviser", fromShopAdviser != null ? fromShopAdviser : 1)
                    .set("push", "1")
                    .set("shop_id", shopId)
                    .set("customer_openid", targetOpenid)
                    .set("is_read", 1)
                    .set("status", 1)
                    .set("create_time", new Date())
                    .set("update_time", new Date());

            Long operatorSchemeId = getDb().insertForGeneratedKey(operatorEntity);
            log.info("保存方案到操作者成功，操作者: {}, 方案ID: {}", operatorOpenid, operatorSchemeId);

            // 3. 创建推送通知
            Entity notification = Entity.create("qj_scheme_notification")
                    .set("target_openid", targetOpenid)
                    .set("scheme_id", targetSchemeId)
                    .set("operator_openid", operatorOpenid)
                    .set("is_read", 0)
                    .set("create_time", new Date());

            getDb().insert(notification);
            log.info("创建推送通知成功，目标用户: {}", targetOpenid);

            JSONObject result = new JSONObject();
            result.set("targetSchemeId", targetSchemeId);
            result.set("operatorSchemeId", operatorSchemeId);
            return AgentResponse.success("推送成功", result);
        } catch (SQLException e) {
            log.error("推送方案失败", e);
            return AgentResponse.fail("推送方案失败：" + e.getMessage());
        }
    }

    /**
     * 获取方案列表（支持sourceType筛选）
     */
    public AgentResponse getSchemeList(String openid, Integer sourceType,
            Integer pageNumber, Integer pageSize) {
        try {
            int page = pageNumber != null ? pageNumber : 1;
            int size = pageSize != null ? pageSize : 10;
            int offset = (page - 1) * size;

            // 自愈兜底：首页加载时，把"只进 TCB、未进 MySQL"的方案补齐到 MySQL。
            // 云函数写后端是 best-effort，遇到后端重启/抖动会静默只写 TCB；
            // 这里让用户下次打开「我的方案」即自动补回。best-effort，不影响列表返回。
            if (page == 1) {
                reconcileFromTcb(openid);
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM qj_history_scheme WHERE openid = ? AND status = 1");

            List<Object> params = new ArrayList<>();
            params.add(openid);

            if (sourceType != null) {
                if (sourceType == 0) {
                    // sourceType=0 表示获取所有推送相关方案（2=代理推送给我，3=我推送给用户）
                    sql.append(" AND source_type IN (2, 3)");
                } else if (sourceType == -1) {
                    // sourceType=-1 表示获取所有类型方案（合并显示：自己生成+代理推送给我）
                    sql.append(" AND source_type IN (1, 2)");
                } else {
                    sql.append(" AND source_type = ?");
                    params.add(sourceType);
                }
            }

            sql.append(" ORDER BY scheme_time DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add(offset);

            List<Entity> list = getDb().query(sql.toString(), params.toArray());

            // 获取总数
            StringBuilder countSql = new StringBuilder();
            countSql.append("SELECT COUNT(*) as total FROM qj_history_scheme WHERE openid = ? AND status = 1");
            List<Object> countParams = new ArrayList<>();
            countParams.add(openid);

            if (sourceType != null) {
                if (sourceType == 0) {
                    countSql.append(" AND source_type IN (2, 3)");
                } else if (sourceType == -1) {
                    countSql.append(" AND source_type IN (1, 2)");
                } else {
                    countSql.append(" AND source_type = ?");
                    countParams.add(sourceType);
                }
            }

            Number total = (Number) getDb().queryNumber(countSql.toString(), countParams.toArray());

            Map<Long, Integer> aiTryOnCountMap = new HashMap<>();
            if (!list.isEmpty()) {
                StringBuilder ids = new StringBuilder();
                List<Object> tryOnParams = new ArrayList<>();
                for (Entity entity : list) {
                    Long id = entity.getLong("id");
                    if (id == null) {
                        continue;
                    }
                    if (ids.length() > 0) {
                        ids.append(",");
                    }
                    ids.append("?");
                    tryOnParams.add(id);
                }
                if (!tryOnParams.isEmpty()) {
                    List<Entity> tryOnRows = getDb().query(
                            "SELECT scheme_id, COUNT(*) AS total FROM qj_generated_outfit_image "
                                    + "WHERE status = 1 AND scheme_id IN (" + ids + ") GROUP BY scheme_id",
                            tryOnParams.toArray());
                    for (Entity row : tryOnRows) {
                        Long schemeId = row.getLong("scheme_id");
                        Number count = (Number) row.get("total");
                        if (schemeId != null && count != null) {
                            aiTryOnCountMap.put(schemeId, count.intValue());
                        }
                    }
                }
            }

            // 转换结果
            JSONArray dataList = new JSONArray();
            for (Entity entity : list) {
                Long id = entity.getLong("id");
                int aiTryOnCount = aiTryOnCountMap.getOrDefault(id, 0);
                JSONObject item = new JSONObject();
                item.set("id", id);
                item.set("openid", entity.getStr("openid"));
                item.set("operatorOpenid", entity.getStr("operator_openid"));
                // 直接返回字符串，让前端自己解析
                item.set("collocationData", entity.getStr("collocation_data"));
                item.set("humanData", entity.getStr("human_data"));
                item.set("schemeTime", entity.getLong("scheme_time"));
                item.set("sourceType", entity.getInt("source_type"));
                item.set("isRead", entity.getInt("is_read"));
                item.set("aiTryOnCount", aiTryOnCount);
                item.set("hasAiTryOn", aiTryOnCount > 0);
                item.set("createTime", entity.getDate("create_time"));
                dataList.add(item);
            }

            JSONObject result = new JSONObject();
            result.set("list", dataList);
            result.set("total", total);
            result.set("pageNumber", page);
            result.set("pageSize", size);

            return AgentResponse.success("获取成功", result);
        } catch (SQLException e) {
            log.error("获取方案列表失败", e);
            return AgentResponse.fail("获取方案列表失败：" + e.getMessage());
        }
    }

    /**
     * 检查未读推送通知
     */
    public AgentResponse checkNotification(String openid) {
        try {
            String sql = "SELECT COUNT(*) FROM qj_scheme_notification WHERE target_openid = ? AND is_read = 0";
            Number count = (Number) getDb().queryNumber(sql, openid);

            JSONObject result = new JSONObject();
            result.set("hasUnread", count.intValue() > 0);
            result.set("unreadCount", count.intValue());

            return AgentResponse.success("查询成功", result);
        } catch (SQLException e) {
            log.error("检查通知失败", e);
            return AgentResponse.fail("检查通知失败：" + e.getMessage());
        }
    }

    /**
     * 标记通知已读
     */
    public AgentResponse markNotificationRead(String openid) {
        try {
            int updated = getDb().execute(
                    "UPDATE qj_scheme_notification SET is_read = 1 WHERE target_openid = ? AND is_read = 0",
                    openid);

            // 同时更新方案的is_read状态
            getDb().execute(
                    "UPDATE qj_history_scheme SET is_read = 1 WHERE openid = ? AND source_type = 2 AND is_read = 0",
                    openid);

            JSONObject result = new JSONObject();
            result.set("updatedCount", updated);

            return AgentResponse.success("标记成功", result);
        } catch (SQLException e) {
            log.error("标记已读失败", e);
            return AgentResponse.fail("标记已读失败：" + e.getMessage());
        }
    }

    /** TCB 兜底自愈：每次只比对该用户最近这么多条，控制成本 */
    private static final int TCB_RECONCILE_LIMIT = 20;

    /**
     * 自愈兜底：从微信云开发 TCB(history-scheme 集合)拉取该用户最近的方案，
     * 补齐"因云函数写后端失败而只进 TCB、未进 MySQL"的方案（按 openid+scheme_time 去重）。
     * 补进来的行 source_type=1（自己生成）、保留原始 scheme_time，并获得真实自增 id，
     * 使「我的方案」可见、AI 试穿也能正常归属。
     * 全程 best-effort：任何异常都吞掉，绝不影响列表主流程。仅首页触发、限最近 N 条。
     */
    private void reconcileFromTcb(String openid) {
        // openid 仅允许微信合法字符，避免拼进 TCB 查询串造成注入
        if (openid == null || !openid.matches("^[A-Za-z0-9_-]{1,64}$")) {
            return;
        }
        try {
            String token = AccessTokenUtil.getAccessToken();
            String env = AccessTokenUtil.getEnv();
            if (token == null || env == null) {
                return;
            }
            String query = "db.collection('history-scheme').where({_openid:'" + openid
                    + "'}).orderBy('schemeTime','desc').limit(" + TCB_RECONCILE_LIMIT + ").get()";
            JSONObject reqBody = new JSONObject();
            reqBody.set("env", env);
            reqBody.set("query", query);

            String resp = HttpRequest
                    .post("https://api.weixin.qq.com/tcb/databasequery?access_token=" + token)
                    .header("Content-Type", "application/json")
                    .body(reqBody.toString())
                    .timeout(3000) // 列表主链路同步等，控制在 3s，TCB 慢/挂也不拖垮列表
                    .execute().body();

            JSONObject respObj = JSONUtil.parseObj(resp);
            if (respObj.getInt("errcode", -1) != 0) {
                log.warn("TCB 兜底查询返回错误：{}", resp);
                return;
            }
            JSONArray data = respObj.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return;
            }

            // 一次性取出该用户已有的 scheme_time，内存比对，避免逐条查库
            Set<Long> existing = new HashSet<>();
            List<Entity> rows = getDb().query(
                    "SELECT scheme_time FROM qj_history_scheme WHERE openid = ?", openid);
            for (Entity r : rows) {
                Long t = r.getLong("scheme_time");
                if (t != null) {
                    existing.add(t);
                }
            }

            int healed = 0;
            for (Object o : data) {
                try {
                    JSONObject doc = JSONUtil.parseObj(o.toString());
                    Long schemeTime = doc.getLong("schemeTime");
                    if (schemeTime == null || existing.contains(schemeTime)) {
                        continue;
                    }
                    String collocationData = stringifyField(doc.get("collocationData"));
                    String humanData = stringifyField(doc.get("humanData"));
                    if (collocationData == null || humanData == null) {
                        continue; // 数据不全不补，避免脏数据
                    }
                    Entity entity = Entity.create("qj_history_scheme")
                            .set("openid", openid)
                            .set("collocation_data", collocationData)
                            .set("human_data", humanData)
                            .set("scheme_time", schemeTime)
                            .set("source_type", 1) // 自己生成，与 addScheme 一致
                            .set("from_shop_adviser", doc.getInt("fromshopadviser", 0))
                            .set("push", doc.getStr("push"))
                            .set("shop_id", doc.getInt("shopId"))
                            .set("customer_openid", doc.getStr("customerOpenid"))
                            .set("is_read", 1)
                            .set("status", 1)
                            .set("create_time", new Date())
                            .set("update_time", new Date());
                    getDb().insert(entity);
                    existing.add(schemeTime);
                    healed++;
                } catch (Exception inner) {
                    log.warn("TCB 兜底单条补齐失败（忽略该条）", inner);
                }
            }
            if (healed > 0) {
                log.info("TCB 兜底自愈：openid={} 补齐 {} 条历史方案", openid, healed);
            }
        } catch (Exception e) {
            log.warn("TCB 兜底自愈异常（忽略，不影响列表）", e);
        }
    }

    /** TCB 字段可能是对象或字符串：对象转 JSON 字符串，字符串原样返回 */
    private String stringifyField(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof CharSequence) {
            return v.toString();
        }
        return JSONUtil.toJsonStr(v);
    }
}
