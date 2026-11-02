package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 代理审批推广关系变动日志服务
 */
@Service
public class AgentPromotionLogService {

    private static final Logger log = LoggerFactory.getLogger(AgentPromotionLogService.class);

    /**
     * 记录代理审批推广关系变动日志
     * 
     * @param applicantOpenid 申请人openid
     * @param applyRole 申请的代理等级
     * @param oldRole 原代理等级
     */
    public void recordPromotionLog(String applicantOpenid, Integer applyRole, Integer oldRole) {
        try {
            log.info("开始记录代理审批推广关系变动日志：applicant={}, applyRole={}, oldRole={}", 
                    applicantOpenid, applyRole, oldRole);

            // 1. 获取申请人昵称
            String applicantNickname = getUserNickname(applicantOpenid);

            // 2. 获取角色名称
            String applyRoleName = getRoleNameByRole(applyRole);
            String oldRoleName = getRoleNameByRole(oldRole);

            // 3. 获取推广关系变动信息
            PromotionRelationChange relationChange = getPromotionRelationChange(applicantOpenid, applyRole, oldRole);

            // 4. 构建日志记录
            JSONObject logParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("applicant_openid");
            fieldKeys.add("applicant_nickname");
            fieldKeys.add("apply_role");
            fieldKeys.add("apply_role_name");
            fieldKeys.add("old_role");
            fieldKeys.add("old_role_name");
            fieldKeys.add("promotion_relation_changes");

            logParam.set("__mysql_fieldkeys__", fieldKeys);
            logParam.set("applicant_openid", applicantOpenid);
            logParam.set("applicant_nickname", applicantNickname);
            logParam.set("apply_role", applyRole);
            logParam.set("apply_role_name", applyRoleName);
            logParam.set("old_role", oldRole);
            logParam.set("old_role_name", oldRoleName);
            logParam.set("promotion_relation_changes", relationChange.getChangesJson());

            // 5. 插入日志记录
            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4209_AGENT_PROMOTION_LOG_CREATE,
                    "F_303_4209_AGENT_PROMOTION_LOG_CREATE",
                    "qj_marketing_v2_agent_promotion_logs",
                    logParam,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("代理审批推广关系变动日志记录成功：applicant={}", applicantOpenid);
            } else {
                log.error("代理审批推广关系变动日志记录失败：applicant={}, error={}", 
                        applicantOpenid, result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("记录代理审批推广关系变动日志异常：applicant={}", applicantOpenid, e);
        }
    }

    /**
     * 记录代理失去资格时的推广关系变动日志
     *
     * @param expiredAgentOpenid 失去资格的代理openid
     * @param expiredAgentRole 失去资格的代理等级
     * @param affectedUserOpenids 受影响的用户openid列表
     * @param reason 失去资格的原因（如：到期、暂停、取消等）
     * @param migrationDetails 迁移详情（用户迁移到哪个新上级的信息）
     */
    public void recordAgentExpiredLog(String expiredAgentOpenid, Integer expiredAgentRole,
                                     List<String> affectedUserOpenids, String reason, String migrationDetails) {
        try {
            log.info("开始记录代理失去资格推广关系变动日志：expiredAgent={}, role={}, affectedUsers={}, reason={}",
                    expiredAgentOpenid, expiredAgentRole, affectedUserOpenids.size(), reason);

            // 1. 获取失去资格代理的昵称
            String expiredAgentNickname = getUserNickname(expiredAgentOpenid);

            // 2. 获取角色名称
            String expiredRoleName = getRoleNameByRole(expiredAgentRole);

            // 3. 构建推广关系变动信息 - 包含迁移详情
            JSONObject relationChanges = new JSONObject();
            String baseMessage = String.format("%s%s失去资格（%s），影响%d个下级用户的推广关系",
                    expiredRoleName != null ? expiredRoleName : "代理", expiredAgentOpenid, reason, affectedUserOpenids.size());

            String fullMessage = baseMessage;
            if (StrUtil.isNotBlank(migrationDetails)) {
                fullMessage = baseMessage + "，" + migrationDetails;
            }

            relationChanges.set("relation_change", fullMessage);

            // 4. 构建日志记录
            JSONObject logParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("applicant_openid");
            fieldKeys.add("applicant_nickname");
            fieldKeys.add("apply_role");
            fieldKeys.add("apply_role_name");
            fieldKeys.add("old_role");
            fieldKeys.add("old_role_name");
            fieldKeys.add("promotion_relation_changes");

            logParam.set("__mysql_fieldkeys__", fieldKeys);
            logParam.set("applicant_openid", expiredAgentOpenid);
            logParam.set("applicant_nickname", expiredAgentNickname);
            logParam.set("apply_role", 0); // 失去资格后变为普通用户
            logParam.set("apply_role_name", "普通用户");
            logParam.set("old_role", expiredAgentRole);
            logParam.set("old_role_name", expiredRoleName);
            logParam.set("promotion_relation_changes", relationChanges.toString());

            // 5. 插入日志记录
            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4209_AGENT_PROMOTION_LOG_CREATE,
                    "F_303_4209_AGENT_PROMOTION_LOG_CREATE",
                    "qj_marketing_v2_agent_promotion_logs",
                    logParam,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("代理失去资格推广关系变动日志记录成功：expiredAgent={}", expiredAgentOpenid);
            } else {
                log.error("代理失去资格推广关系变动日志记录失败：expiredAgent={}, error={}",
                        expiredAgentOpenid, result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("记录代理失去资格推广关系变动日志异常：expiredAgent={}", expiredAgentOpenid, e);
        }
    }


    /**
     * 记录普通用户超时未升级清除推广关系日志
     *
     * @param userOpenid 超时用户openid
     * @param agentOpenid 原上级代理openid
     * @param daysSinceJoin 建立推广关系的天数
     */
    public void recordUserTimeoutLog(String userOpenid, String agentOpenid, long daysSinceJoin) {
        try {
            log.info("开始记录普通用户超时未升级推广关系变动日志：user={}, agent={}, days={}",
                    userOpenid, agentOpenid, daysSinceJoin);

            // 1. 获取用户和代理昵称
            String userNickname = getUserNickname(userOpenid);
            String agentNickname = getUserNickname(agentOpenid);

            // 2. 获取代理角色信息
            Integer agentRole = getAgentRole(agentOpenid);
            String agentRoleName = getRoleNameByRole(agentRole);

            // 3. 构建推广关系变动信息 - 简化为只保留一句描述
            JSONObject relationChanges = new JSONObject();
            relationChanges.set("relation_change", String.format("普通用户%s在%d天内未升级为代理，清除与%s%s的推广关系",
                    userOpenid, daysSinceJoin, agentRoleName != null ? agentRoleName : "代理", agentOpenid));

            // 4. 构建日志记录
            JSONObject logParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("applicant_openid");
            fieldKeys.add("applicant_nickname");
            fieldKeys.add("apply_role");
            fieldKeys.add("apply_role_name");
            fieldKeys.add("old_role");
            fieldKeys.add("old_role_name");
            fieldKeys.add("promotion_relation_changes");

            logParam.set("__mysql_fieldkeys__", fieldKeys);
            logParam.set("applicant_openid", userOpenid);
            logParam.set("applicant_nickname", userNickname);
            logParam.set("apply_role", 0); // 仍然是普通用户
            logParam.set("apply_role_name", "普通用户");
            logParam.set("old_role", 0); // 原来也是普通用户
            logParam.set("old_role_name", "普通用户");
            logParam.set("promotion_relation_changes", relationChanges.toString());

            // 5. 插入日志记录
            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4209_AGENT_PROMOTION_LOG_CREATE,
                    "F_303_4209_AGENT_PROMOTION_LOG_CREATE",
                    "qj_marketing_v2_agent_promotion_logs",
                    logParam,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("普通用户超时未升级推广关系变动日志记录成功：user={}", userOpenid);
            } else {
                log.error("普通用户超时未升级推广关系变动日志记录失败：user={}, error={}",
                        userOpenid, result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("记录普通用户超时未升级推广关系变动日志异常：user={}", userOpenid, e);
        }
    }

    /**
     * 获取推广关系变动信息
     */
    private PromotionRelationChange getPromotionRelationChange(String applicantOpenid, Integer applyRole, Integer oldRole) {
        PromotionRelationChange change = new PromotionRelationChange();

        try {
            // 获取当前推广关系
            String currentSuperiorOpenid = getCurrentSuperiorOpenid(applicantOpenid);

            // 检查是否真的存在活跃的推广关系
            boolean hasRealRelationship = hasActivePromotionRelationship(applicantOpenid);

            // 根据申请角色和当前推广关系构建变动描述
            if (StrUtil.isNotBlank(currentSuperiorOpenid) && hasRealRelationship) {
                // 有上级推广关系的情况
                String oldSuperiorNickname = getUserNickname(currentSuperiorOpenid);
                Integer oldSuperiorRole = getAgentRole(currentSuperiorOpenid);
                String oldSuperiorRoleName = getRoleNameByRole(oldSuperiorRole);

                // 根据申请角色确定推广关系变动
                if (applyRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                    // 个人代理申请 - 保持原有推广关系
                    change.setChangesJson(String.format(
                        "{\"relation_change\":\"继续与%s%s保持推广关系\"}",
                        oldSuperiorRoleName, currentSuperiorOpenid
                    ));
                } else if (applyRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                    // 平台代理申请 - 需要查找新的上级城市代理
                    handlePlatformAgentPromotion(change, currentSuperiorOpenid, oldSuperiorRoleName, oldRole);
                } else if (applyRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    // 城市代理申请 - 成为独立个体
                    // 检查是否真的有推广关系存在
                    if (StrUtil.isNotBlank(currentSuperiorOpenid)) {
                        change.setChangesJson(String.format(
                            "{\"relation_change\":\"与%s%s解除关系，成为独立城市代理\"}",
                            oldSuperiorRoleName, currentSuperiorOpenid
                        ));
                    } else {
                        change.setChangesJson(
                            "{\"relation_change\":\"成为独立城市代理\"}"
                        );
                    }
                }
            } else {
                // 没有上级推广关系的情况
                if (applyRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                    // 平台代理申请 - 推广关系待建立
                    if (oldRole != null && oldRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                        change.setChangesJson(
                            "{\"relation_change\":\"个人代理升级为平台代理，推广关系待建立\"}"
                        );
                    } else {
                        change.setChangesJson(
                            "{\"relation_change\":\"成为平台代理，推广关系待建立\"}"
                        );
                    }
                } else if (applyRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    // 升级为城市代理 - 成为独立个体
                    if (oldRole != null && oldRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                        change.setChangesJson(
                            "{\"relation_change\":\"个人代理升级为城市代理，成为独立个体\"}"
                        );
                    } else if (oldRole != null && oldRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                        change.setChangesJson(
                            "{\"relation_change\":\"平台代理升级为城市代理，成为独立个体\"}"
                        );
                    } else {
                        change.setChangesJson(
                            "{\"relation_change\":\"成为城市代理，无上级关系\"}"
                        );
                    }
                } else if (applyRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                    // 普通用户申请个人代理 - 需要分配上级
                    if (oldRole == null) {
                        change.setChangesJson(
                            "{\"relation_change\":\"成为个人代理，等待分配上级\"}"
                        );
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取推广关系变动信息异常：applicant={}", applicantOpenid, e);
        }

        return change;
    }

    /**
     * 处理平台代理申请的推广关系变动
     */
    private void handlePlatformAgentPromotion(PromotionRelationChange change, String currentSuperiorOpenid,
                                            String oldSuperiorRoleName, Integer oldRole) {
        // 检查当前上级的角色，决定如何处理推广关系
        Integer currentSuperiorRole = getAgentRole(currentSuperiorOpenid);

        if (currentSuperiorRole != null && currentSuperiorRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
            // 当前上级是平台代理，升级为平台代理后需要重新分配到城市代理
            // 查找当前平台代理的上级（城市代理）
            String cityAgentOpenid = getCurrentSuperiorOpenid(currentSuperiorOpenid);

            if (StrUtil.isNotBlank(cityAgentOpenid)) {
                Integer cityAgentRole = getAgentRole(cityAgentOpenid);
                if (cityAgentRole != null && cityAgentRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    // 有城市代理上级
                    String cityAgentRoleName = getRoleNameByRole(cityAgentRole);

                    change.setChangesJson(String.format(
                        "{\"relation_change\":\"与%s%s解除关系，转为与%s%s建立关系\"}",
                        oldSuperiorRoleName, currentSuperiorOpenid,
                        cityAgentRoleName, cityAgentOpenid
                    ));
                    return;
                }
            }

            // 没有城市代理上级，与官方城市代理建立关系
            change.setChangesJson(String.format(
                "{\"relation_change\":\"与%s%s解除关系，转为与官方城市代理建立关系\"}",
                oldSuperiorRoleName, currentSuperiorOpenid
            ));
        } else if (currentSuperiorRole != null && currentSuperiorRole == AgentStatusCodes.ROLE_CITY_AGENT) {
            // 当前上级是城市代理，升级为平台代理后继续保持关系
            change.setChangesJson(String.format(
                "{\"relation_change\":\"继续与%s%s保持推广关系\"}",
                oldSuperiorRoleName, currentSuperiorOpenid
            ));
        } else {
            // 其他情况（如个人代理上级），与官方城市代理建立关系
            change.setChangesJson(String.format(
                "{\"relation_change\":\"与%s%s解除关系，转为与官方城市代理建立关系\"}",
                oldSuperiorRoleName, currentSuperiorOpenid
            ));
        }
    }

    /**
     * 获取当前上级代理openid
     */
    private String getCurrentSuperiorOpenid(String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", userOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                if (relations != null && !relations.isEmpty()) {
                    return (String) relations.get(0).get("agent_openid");
                }
            }

            return null;
        } catch (Exception e) {
            log.error("获取当前上级代理异常：userOpenid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取代理等级
     */
    private Integer getAgentRole(String openid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agents = (List<Map<String, Object>>) result.get("result");
                if (agents != null && !agents.isEmpty()) {
                    return Convert.toInt(agents.get(0).get("current_role"));
                }
            }

            return null;
        } catch (Exception e) {
            log.error("获取代理等级异常：openid={}", openid, e);
            return null;
        }
    }

    /**
     * 获取用户昵称
     */
    private String getUserNickname(String openid) {
        try {
            if (StrUtil.isBlank(openid)) {
                return null;
            }

            // 特殊处理官方城市代理
            if ("ocDgH0bxhV98Pyvw1Mz0HOwBWIV8".equals(openid)) {
                return "官方城市代理";
            }

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("_openid");
            fieldKeys.add("is_del");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("_openid", openid);
            param.set("is_del", 0);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2012_MEMBER_GET_NICKNAME,
                    "F_101_2012_MEMBER_GET_NICKNAME",
                    "qj_member",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> members = (List<Map<String, Object>>) result.get("result");
                if (members != null && !members.isEmpty()) {
                    Object nickname = members.get(0).get("nick_name");
                    return nickname != null ? nickname.toString() : openid;
                }
            }

            return openid; // 找不到昵称时返回openid
        } catch (Exception e) {
            log.error("获取用户昵称异常：openid={}", openid, e);
            return openid;
        }
    }

    /**
     * 根据角色ID获取角色名称
     */
    private String getRoleNameByRole(Integer role) {
        if (role == null) {
            return "普通用户";
        }
        
        switch (role) {
            case AgentStatusCodes.ROLE_CITY_AGENT:
                return "城市代理";
            case AgentStatusCodes.ROLE_PLATFORM_AGENT:
                return "平台代理";
            case AgentStatusCodes.ROLE_PERSONAL_AGENT:
                return "个人代理";
            default:
                return "普通用户";
        }
    }

    /**
     * 推广关系变动信息类
     */
    private static class PromotionRelationChange {
        private String changesJson;

        // Getters and Setters
        public String getChangesJson() { return changesJson; }
        public void setChangesJson(String changesJson) { this.changesJson = changesJson; }
    }

    /**
     * 检查用户是否有活跃的推广关系
     */
    private boolean hasActivePromotionRelationship(String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", userOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                return relations != null && !relations.isEmpty();
            }
            return false;
        } catch (Exception e) {
            log.error("检查推广关系异常：userOpenid={}", userOpenid, e);
            return false;
        }
    }
}
