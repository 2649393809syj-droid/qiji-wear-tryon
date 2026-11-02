package com.qj.controllers;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端搜索接口 - 支持模糊查询
 */
@RestController
@RequestMapping("/qj/admin/search")
public class AdminSearchController {

    private static final Logger log = LoggerFactory.getLogger(AdminSearchController.class);

    /**
     * 用户行为模糊查询
     */
    @PostMapping("/userBehavior")
    public JSONObject searchUserBehavior(@RequestBody String requestBody) {
        log.info("用户行为搜索请求：{}", requestBody);
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid", "");
            String behaviorType = reqJson.getStr("behaviorType", "");
            int pageSize = reqJson.getInt("pageSize", 16);
            int offset = reqJson.getInt("offset", 0);

            // 构建WHERE条件
            StringBuilder whereClause = new StringBuilder("WHERE 1=1 ");
            List<Object> whereParams = new ArrayList<>();
            
            if (openid != null && !openid.trim().isEmpty()) {
                whereClause.append("AND ub.openid LIKE ? ");
                whereParams.add("%" + openid.trim() + "%");
            }
            
            if (behaviorType != null && !behaviorType.trim().isEmpty()) {
                whereClause.append("AND ub.be_type LIKE ? ");
                whereParams.add("%" + behaviorType.trim() + "%");
            }
            
            // 查询数据
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ub.id, ub.openid, ub.be_type as behavior_type, ");
            sql.append("ub.amount_valid, ub.be_date as created_at, m.nick_name ");
            sql.append("FROM qj_marketing_v2_user_behavior ub ");
            sql.append("LEFT JOIN qj_member m ON ub.openid = m._openid AND m.is_del = 0 ");
            sql.append(whereClause);
            sql.append("ORDER BY ub.be_date DESC ");
            sql.append("LIMIT ? OFFSET ?");
            
            List<Object> params = new ArrayList<>(whereParams);
            params.add(pageSize);
            params.add(offset);

            List<Map<String, Object>> list = executeQuery(sql.toString(), params);
            
            // 查询总数
            String countSql = "SELECT COUNT(*) as total FROM qj_marketing_v2_user_behavior ub " + whereClause;
            int total = getTotal(countSql, whereParams);

            result.set("errCode", 0);
            result.set("result", list);
            result.set("total", total);
        } catch (Exception e) {
            log.error("用户行为搜索异常", e);
            result.set("errCode", -1);
            result.set("errMsg", "搜索失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 推广关系模糊查询
     */
    @PostMapping("/agentRelations")
    public JSONObject searchAgentRelations(@RequestBody String requestBody) {
        log.info("推广关系搜索请求：{}", requestBody);
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String agentOpenid = reqJson.getStr("agentOpenid", "");
            String userOpenid = reqJson.getStr("userOpenid", "");
            int pageSize = reqJson.getInt("pageSize", 10);
            int offset = reqJson.getInt("offset", 0);

            // 构建WHERE条件
            StringBuilder whereClause = new StringBuilder("WHERE 1=1 ");
            List<Object> whereParams = new ArrayList<>();
            
            if (agentOpenid != null && !agentOpenid.trim().isEmpty()) {
                whereClause.append("AND au.agent_openid LIKE ? ");
                whereParams.add("%" + agentOpenid.trim() + "%");
            }
            
            if (userOpenid != null && !userOpenid.trim().isEmpty()) {
                whereClause.append("AND au.user_openid LIKE ? ");
                whereParams.add("%" + userOpenid.trim() + "%");
            }
            
            // 查询数据
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT au.agent_openid, au.user_openid, au.join_date as created_at, ");
            sql.append("m1.nick_name as agent_nick_name, ");
            sql.append("CASE a.current_role WHEN 7 THEN '个人代理' WHEN 6 THEN '平台代理' WHEN 5 THEN '城市代理' ELSE '未知代理' END as agent_role_name, ");
            sql.append("m2.nick_name as user_nick_name ");
            sql.append("FROM qj_marketing_v2_agent_users au ");
            sql.append("LEFT JOIN qj_marketing_v2_agents a ON au.agent_openid = a.openid ");
            sql.append("LEFT JOIN qj_member m1 ON au.agent_openid = m1._openid AND m1.is_del = 0 ");
            sql.append("LEFT JOIN qj_member m2 ON au.user_openid = m2._openid AND m2.is_del = 0 ");
            sql.append(whereClause);
            sql.append("ORDER BY au.join_date DESC ");
            sql.append("LIMIT ? OFFSET ?");
            
            List<Object> params = new ArrayList<>(whereParams);
            params.add(pageSize);
            params.add(offset);

            List<Map<String, Object>> list = executeQuery(sql.toString(), params);
            
            // 查询总数
            String countSql = "SELECT COUNT(*) as total FROM qj_marketing_v2_agent_users au " + whereClause;
            int total = getTotal(countSql, whereParams);

            result.set("errCode", 0);
            result.set("result", list);
            result.set("total", total);
        } catch (Exception e) {
            log.error("推广关系搜索异常", e);
            result.set("errCode", -1);
            result.set("errMsg", "搜索失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 日志管理模糊查询
     */
    @PostMapping("/promotionLogs")
    public JSONObject searchPromotionLogs(@RequestBody String requestBody) {
        log.info("日志搜索请求：{}", requestBody);
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String applicantOpenid = reqJson.getStr("applicantOpenid", "");
            String applyRole = reqJson.getStr("applyRole", "");
            int pageSize = reqJson.getInt("pageSize", 10);
            int offset = reqJson.getInt("offset", 0);

            // 构建WHERE条件
            StringBuilder whereClause = new StringBuilder("WHERE 1=1 ");
            List<Object> whereParams = new ArrayList<>();
            
            if (applicantOpenid != null && !applicantOpenid.trim().isEmpty()) {
                whereClause.append("AND applicant_openid LIKE ? ");
                whereParams.add("%" + applicantOpenid.trim() + "%");
            }
            
            if (applyRole != null && !applyRole.trim().isEmpty()) {
                whereClause.append("AND apply_role = ? ");
                whereParams.add(applyRole.trim());
            }
            
            // 查询数据
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM qj_marketing_v2_agent_promotion_logs ");
            sql.append(whereClause);
            sql.append("ORDER BY approval_time DESC ");
            sql.append("LIMIT ? OFFSET ?");
            
            List<Object> params = new ArrayList<>(whereParams);
            params.add(pageSize);
            params.add(offset);

            List<Map<String, Object>> list = executeQuery(sql.toString(), params);
            
            // 查询总数
            String countSql = "SELECT COUNT(*) as total FROM qj_marketing_v2_agent_promotion_logs " + whereClause;
            int total = getTotal(countSql, whereParams);

            result.set("errCode", 0);
            result.set("result", list);
            result.set("total", total);
        } catch (Exception e) {
            log.error("日志搜索异常", e);
            result.set("errCode", -1);
            result.set("errMsg", "搜索失败：" + e.getMessage());
        }
        return result;
    }

    private List<Map<String, Object>> executeQuery(String sql, List<Object> params) throws Exception {
        Db db = DBConfig.getDb(null, "qj");
        log.debug("执行SQL: {}, 参数: {}", sql, params);
        List<Entity> entities = db.query(sql, params.toArray());
        return entities.stream()
                .map(entity -> (Map<String, Object>) new java.util.HashMap<String, Object>(entity))
                .collect(Collectors.toList());
    }

    private int getTotal(String countSql, List<Object> params) throws Exception {
        Db db = DBConfig.getDb(null, "qj");
        List<Entity> result = db.query(countSql, params.toArray());
        if (result != null && !result.isEmpty()) {
            return result.get(0).getInt("total");
        }
        return 0;
    }
}
