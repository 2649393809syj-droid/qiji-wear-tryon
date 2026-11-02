package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.config.MyJsonConfig;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 代理等级变更时的推广关系调整服务
 * 处理代理升级时的推广关系重新分配逻辑
 */
@Service
public class AgentPromotionRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(AgentPromotionRelationshipService.class);

    // 官方固定城市代理openid（确保始终有城市代理）
    private static final String OFFICIAL_CITY_AGENT_OPENID = "ocDgH0bxhV98Pyvw1Mz0HOwBWIV8";

    /**
     * 处理代理等级变更时的推广关系调整
     * 
     * @param openid 用户openid
     * @param oldRole 原等级
     * @param newRole 新等级
     */
    public void handleAgentLevelChange(String openid, Integer oldRole, Integer newRole) {
        if (StrUtil.isBlank(openid) || newRole == null) {
            log.warn("代理等级变更参数无效：openid={}, oldRole={}, newRole={}", openid, oldRole, newRole);
            return;
        }

        log.info("开始处理代理等级变更：openid={}, 原等级={}, 新等级={}", openid, oldRole, newRole);

        try {
            switch (newRole) {
                case AgentStatusCodes.ROLE_CITY_AGENT: // 5 - 城市代理
                    handleUpgradeToCity(openid, oldRole);
                    break;
                case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 6 - 平台代理
                    handleUpgradeToPlatform(openid, oldRole);
                    break;
                case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 7 - 个人代理
                    handleUpgradeToPersonal(openid, oldRole);
                    break;
                default:
                    log.info("普通用户等级变更，无需调整推广关系：openid={}, newRole={}", openid, newRole);
                    break;
            }
        } catch (Exception e) {
            log.error("处理代理等级变更异常：openid={}, oldRole={}, newRole={}", openid, oldRole, newRole, e);
            throw e;
        }
    }

    /**
     * 处理升级为城市代理的推广关系调整
     * 城市代理是最高级，成为城市代理就不能有上级推广关系存在
     */
    private void handleUpgradeToCity(String openid, Integer oldRole) {
        log.info("处理升级为城市代理：openid={}, 原等级={}", openid, oldRole);

        // 1. 解除所有上级推广关系（城市代理不能有上级）
        removeAllSuperiorRelationships(openid);

        log.info("城市代理升级处理完成：openid={}", openid);
    }

    /**
     * 处理升级为平台代理的推广关系调整
     * 个人代理申请成为平台代理，同意之后与当前关联的平台代理解除推广关系，与当前平台代理上级的城市代理建立关系
     */
    private void handleUpgradeToPlatform(String openid, Integer oldRole) {
        log.info("处理升级为平台代理：openid={}, 原等级={}", openid, oldRole);

        // 检查用户当前是否有推广关系
        String currentAgentOpenid = getCurrentAgentOpenid(openid);
        if (StrUtil.isNotBlank(currentAgentOpenid)) {
            // 用户有推广关系，检查上级代理的等级
            Integer currentAgentRole = getAgentRole(currentAgentOpenid);
            log.info("用户{}当前上级代理：{}，等级：{}", openid, currentAgentOpenid, currentAgentRole);

            if (currentAgentRole != null && currentAgentRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                // 上级是平台代理，按个人代理升级为平台代理的逻辑处理
                log.info("用户{}的上级是平台代理，按个人代理升级逻辑处理", openid);
                handlePersonalToPlatformUpgrade(openid);
            } else {
                // 上级不是平台代理，按一般平台代理升级逻辑处理
                log.info("用户{}的上级不是平台代理，按一般平台代理升级逻辑处理", openid);
                ensurePlatformAgentHasCityAgent(openid);
            }
        } else {
            // 用户没有推广关系，平台代理推广关系待建立，不自动绑定
            log.info("用户{}没有推广关系，平台代理推广关系待建立，不自动绑定", openid);
        }

        log.info("平台代理升级处理完成：openid={}", openid);
    }

    /**
     * 处理升级为个人代理的推广关系调整
     */
    private void handleUpgradeToPersonal(String openid, Integer oldRole) {
        log.info("处理升级为个人代理：openid={}, 原等级={}", openid, oldRole);
        // 个人代理可以有推广关系，无需特殊处理
        log.info("个人代理升级处理完成：openid={}", openid);
    }

    /**
     * 处理个人代理升级为平台代理的特殊逻辑
     */
    private void handlePersonalToPlatformUpgrade(String openid) {
        log.info("处理个人代理升级为平台代理：openid={}", openid);

        // 1. 获取当前的推广关系
        String currentAgentOpenid = getCurrentAgentOpenid(openid);
        if (StrUtil.isBlank(currentAgentOpenid)) {
            log.info("个人代理没有上级推广关系，平台代理推广关系待建立：openid={}", openid);
            return;
        }

        log.info("个人代理当前上级：agent={}, user={}", currentAgentOpenid, openid);

        // 2. 检查当前上级是否为平台代理
        Integer agentRole = getAgentRole(currentAgentOpenid);
        if (agentRole == null || agentRole != AgentStatusCodes.ROLE_PLATFORM_AGENT) {
            log.info("当前上级不是平台代理，无需调整：agent={}, role={}", currentAgentOpenid, agentRole);
            return;
        }

        // 3. 获取平台代理的上级城市代理
        String cityAgentOpenid = getCurrentAgentOpenid(currentAgentOpenid);
        if (StrUtil.isBlank(cityAgentOpenid)) {
            log.info("平台代理没有上级，与官方城市代理建立关系：platform={}", currentAgentOpenid);
            cityAgentOpenid = OFFICIAL_CITY_AGENT_OPENID;
        } else {
            // 验证上级是否为城市代理
            Integer cityAgentRole = getAgentRole(cityAgentOpenid);
            if (cityAgentRole != null && cityAgentRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                log.info("平台代理的上级是城市代理，与该城市代理建立关系：city={}, role={}", cityAgentOpenid, cityAgentRole);
                // cityAgentOpenid 保持不变，使用找到的城市代理
            } else {
                log.info("平台代理的上级不是城市代理，使用官方城市代理：city={}, role={}", cityAgentOpenid, cityAgentRole);
                cityAgentOpenid = OFFICIAL_CITY_AGENT_OPENID;
            }
        }

        // 4. 解除与平台代理的推广关系
        removePromotionRelationship(currentAgentOpenid, openid);

        // 5. 与城市代理建立推广关系
        createPromotionRelationship(cityAgentOpenid, openid);

        log.info("个人代理升级为平台代理完成：user={}, 原平台代理={}, 新城市代理={}", 
                openid, currentAgentOpenid, cityAgentOpenid);
    }

    /**
     * 确保平台代理有城市代理上级
     */
    private void ensurePlatformAgentHasCityAgent(String openid) {
        String currentAgentOpenid = getCurrentAgentOpenid(openid);
        
        if (StrUtil.isBlank(currentAgentOpenid)) {
            // 没有上级，平台代理推广关系待建立
            log.info("平台代理没有上级，推广关系待建立：openid={}", openid);
            return;
        }

        // 检查上级是否为城市代理
        Integer agentRole = getAgentRole(currentAgentOpenid);
        if (agentRole == null || agentRole != AgentStatusCodes.ROLE_CITY_AGENT) {
            log.info("平台代理的上级不是城市代理，调整为官方城市代理：platform={}, currentAgent={}, role={}", 
                    openid, currentAgentOpenid, agentRole);
            
            // 解除当前关系
            removePromotionRelationship(currentAgentOpenid, openid);
            
            // 与官方城市代理建立关系
            createPromotionRelationship(OFFICIAL_CITY_AGENT_OPENID, openid);
        } else {
            log.info("平台代理已有正确的城市代理上级：platform={}, city={}", openid, currentAgentOpenid);
        }
    }

    /**
     * 解除所有上级推广关系
     */
    private void removeAllSuperiorRelationships(String openid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", openid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4211_AGENT_USER_DELETE_ALL_SUPERIOR,
                    "F_303_4211_AGENT_USER_DELETE_ALL_SUPERIOR",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("成功解除所有上级推广关系：openid={}", openid);
            } else {
                log.error("解除上级推广关系失败：openid={}, error={}", openid, result.getStr("errMsg"));
            }
        } catch (Exception e) {
            log.error("解除上级推广关系异常：openid={}", openid, e);
        }
    }

    /**
     * 获取当前用户的上级代理openid
     */
    private String getCurrentAgentOpenid(String userOpenid) {
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
                    String agentOpenid = Convert.toStr(relations.get(0).get("agent_openid"));
                    log.info("获取到用户{}的上级代理：{}", userOpenid, agentOpenid);
                    return agentOpenid;
                }
            }

            log.info("用户{}没有上级代理", userOpenid);
            return null;
        } catch (Exception e) {
            log.error("获取上级代理异常：userOpenid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取代理的等级
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
                    Integer currentRole = Convert.toInt(agents.get(0).get("current_role"));
                    Integer status = Convert.toInt(agents.get(0).get("status"));
                    
                    // 只有状态为有效的代理才返回角色
                    if (status != null && status == AgentStatusCodes.AGENT_STATUS_VALID && currentRole != null) {
                        log.info("获取到代理{}的等级：{}", openid, currentRole);
                        return currentRole;
                    }
                }
            }

            log.info("代理{}没有有效等级", openid);
            return null;
        } catch (Exception e) {
            log.error("获取代理等级异常：openid={}", openid, e);
            return null;
        }
    }

    /**
     * 解除推广关系
     */
    private void removePromotionRelationship(String agentOpenid, String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            fieldKeys.add("agent_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", userOpenid);
            param.set("agent_openid", agentOpenid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4207_AGENT_USER_DELETE,
                    "F_303_4207_AGENT_USER_DELETE",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("成功解除推广关系：agent={}, user={}", agentOpenid, userOpenid);
            } else {
                log.error("解除推广关系失败：agent={}, user={}, error={}",
                        agentOpenid, userOpenid, result.getStr("errMsg"));
            }
        } catch (Exception e) {
            log.error("解除推广关系异常：agent={}, user={}", agentOpenid, userOpenid, e);
        }
    }

    /**
     * 创建推广关系
     */
    private void createPromotionRelationship(String agentOpenid, String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("agent_openid");
            fieldKeys.add("user_openid");
            fieldKeys.add("join_date");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("agent_openid", agentOpenid);
            param.set("user_openid", userOpenid);
            param.set("join_date", new java.util.Date());

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4201_AGENT_USER_CREATE,
                    "F_303_4201_AGENT_USER_CREATE",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("成功创建推广关系：agent={}, user={}", agentOpenid, userOpenid);
            } else {
                log.error("创建推广关系失败：agent={}, user={}, error={}",
                        agentOpenid, userOpenid, result.getStr("errMsg"));
            }
        } catch (Exception e) {
            log.error("创建推广关系异常：agent={}, user={}", agentOpenid, userOpenid, e);
        }
    }

    /**
     * 验证推广关系等级规则
     *
     * @param agentOpenid 推广者openid
     * @param userOpenid 被推广者openid
     * @return 是否符合等级规则
     */
    public boolean validatePromotionHierarchy(String agentOpenid, String userOpenid) {
        try {
            Integer agentRole = getAgentRole(agentOpenid);
            Integer userRole = getAgentRole(userOpenid);

            log.info("验证推广关系等级规则：agent={}, agentRole={}, user={}, userRole={}",
                    agentOpenid, agentRole, userOpenid, userRole);

            // 如果推广者不是代理，不能推广
            if (agentRole == null) {
                log.info("推广者不是代理，不能推广：agent={}", agentOpenid);
                return false;
            }

            // 如果被推广者已经是代理，检查等级规则
            if (userRole != null) {
                // 被推广者已经是代理，检查是否符合等级规则
                return validateAgentToAgentPromotion(agentRole, userRole);
            }

            // 被推广者是普通用户，检查推广者等级
            return validateAgentToUserPromotion(agentRole);

        } catch (Exception e) {
            log.error("验证推广关系等级规则异常：agent={}, user={}", agentOpenid, userOpenid, e);
            return false;
        }
    }

    /**
     * 验证代理对代理的推广规则
     */
    private boolean validateAgentToAgentPromotion(Integer agentRole, Integer userRole) {
        // 城市代理下面只能有平台代理及以下等级
        if (agentRole == AgentStatusCodes.ROLE_CITY_AGENT) {
            return userRole >= AgentStatusCodes.ROLE_PLATFORM_AGENT; // 6, 7 或更高
        }

        // 平台代理下面只能有个人代理和普通用户
        if (agentRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
            return userRole == AgentStatusCodes.ROLE_PERSONAL_AGENT; // 只能是个人代理
        }

        // 个人代理与个人代理可以建立推广关系
        if (agentRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
            return userRole == AgentStatusCodes.ROLE_PERSONAL_AGENT; // 只能推广个人代理
        }

        return false;
    }

    /**
     * 验证代理对普通用户的推广规则
     */
    private boolean validateAgentToUserPromotion(Integer agentRole) {
        // 所有等级的代理都可以推广普通用户
        return agentRole == AgentStatusCodes.ROLE_CITY_AGENT ||
               agentRole == AgentStatusCodes.ROLE_PLATFORM_AGENT ||
               agentRole == AgentStatusCodes.ROLE_PERSONAL_AGENT;
    }

    /**
     * 处理代理到期时的推广关系重新分配
     *
     * @param expiredAgentOpenid 到期代理的openid
     * @param expiredAgentRole 到期代理的等级
     * @param subordinateRelations 下级推广关系列表
     * @return 处理的关系数量
     */
    public int handleExpiredAgentRelationships(String expiredAgentOpenid, Integer expiredAgentRole,
                                               List<Map<String, Object>> subordinateRelations) {
        log.info("开始处理代理到期的推广关系重新分配：expiredAgent={}, role={}, subordinateCount={}",
                expiredAgentOpenid, expiredAgentRole, subordinateRelations.size());

        int processedCount = 0;

        try {
            // 根据到期代理的等级确定重新分配策略
            if (expiredAgentRole != null) {
                switch (expiredAgentRole) {
                    case AgentStatusCodes.ROLE_CITY_AGENT: // 5 - 城市代理到期
                        processedCount = handleExpiredCityAgent(expiredAgentOpenid, subordinateRelations);
                        break;
                    case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 6 - 平台代理到期
                        processedCount = handleExpiredPlatformAgent(expiredAgentOpenid, subordinateRelations);
                        break;
                    case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 7 - 个人代理到期
                        processedCount = handleExpiredPersonalAgent(expiredAgentOpenid, subordinateRelations);
                        break;
                    default:
                        log.info("未知代理等级，使用默认处理方式：role={}", expiredAgentRole);
                        processedCount = handleExpiredDefaultAgent(expiredAgentOpenid, subordinateRelations);
                        break;
                }
            } else {
                log.info("代理等级为空，使用默认处理方式");
                processedCount = handleExpiredDefaultAgent(expiredAgentOpenid, subordinateRelations);
            }

            log.info("代理到期推广关系重新分配完成：expiredAgent={}, role={}, processedCount={}",
                    expiredAgentOpenid, expiredAgentRole, processedCount);

        } catch (Exception e) {
            log.error("处理代理到期推广关系重新分配异常：expiredAgent={}, role={}",
                    expiredAgentOpenid, expiredAgentRole, e);
            throw e;
        }

        return processedCount;
    }

    /**
     * 处理城市代理到期
     * 城市代理到期，其下级自动与官方城市代理建立关系
     */
    private int handleExpiredCityAgent(String expiredCityAgentOpenid, List<Map<String, Object>> subordinateRelations) {
        log.info("处理城市代理到期：expiredCityAgent={}, subordinateCount={}",
                expiredCityAgentOpenid, subordinateRelations.size());

        int processedCount = 0;

        for (Map<String, Object> relation : subordinateRelations) {
            String userOpenid = (String) relation.get("user_openid");
            if (StrUtil.isBlank(userOpenid)) {
                continue;
            }

            try {
                // 1. 解除与到期城市代理的关系
                removePromotionRelationship(expiredCityAgentOpenid, userOpenid);

                // 2. 与官方城市代理建立关系
                createPromotionRelationship(OFFICIAL_CITY_AGENT_OPENID, userOpenid);

                processedCount++;
                log.info("城市代理到期重新分配：user={}, 原城市代理={}, 新城市代理={}",
                        userOpenid, expiredCityAgentOpenid, OFFICIAL_CITY_AGENT_OPENID);

            } catch (Exception e) {
                log.error("处理城市代理到期关系重新分配异常：user={}, expiredCityAgent={}",
                        userOpenid, expiredCityAgentOpenid, e);
            }
        }

        return processedCount;
    }

    /**
     * 处理平台代理到期
     * 平台代理到期，其下级自动与其上级城市代理建立关系
     */
    private int handleExpiredPlatformAgent(String expiredPlatformAgentOpenid, List<Map<String, Object>> subordinateRelations) {
        log.info("处理平台代理到期：expiredPlatformAgent={}, subordinateCount={}",
                expiredPlatformAgentOpenid, subordinateRelations.size());

        // 1. 获取到期平台代理的上级城市代理
        String cityAgentOpenid = getCurrentAgentOpenid(expiredPlatformAgentOpenid);
        if (StrUtil.isBlank(cityAgentOpenid)) {
            log.info("到期平台代理没有上级城市代理，使用官方城市代理：expiredPlatformAgent={}", expiredPlatformAgentOpenid);
            cityAgentOpenid = OFFICIAL_CITY_AGENT_OPENID;
        } else {
            // 验证上级是否为城市代理
            Integer cityAgentRole = getAgentRole(cityAgentOpenid);
            if (cityAgentRole == null || cityAgentRole != AgentStatusCodes.ROLE_CITY_AGENT) {
                log.info("到期平台代理的上级不是城市代理，使用官方城市代理：expiredPlatformAgent={}, upperAgent={}, role={}",
                        expiredPlatformAgentOpenid, cityAgentOpenid, cityAgentRole);
                cityAgentOpenid = OFFICIAL_CITY_AGENT_OPENID;
            } else {
                log.info("到期平台代理的上级是城市代理，将下级重新分配给该城市代理：expiredPlatformAgent={}, cityAgent={}",
                        expiredPlatformAgentOpenid, cityAgentOpenid);
            }
        }

        int processedCount = 0;

        for (Map<String, Object> relation : subordinateRelations) {
            String userOpenid = (String) relation.get("user_openid");
            if (StrUtil.isBlank(userOpenid)) {
                continue;
            }

            try {
                // 1. 解除与到期平台代理的关系
                removePromotionRelationship(expiredPlatformAgentOpenid, userOpenid);

                // 2. 与城市代理建立关系
                createPromotionRelationship(cityAgentOpenid, userOpenid);

                processedCount++;
                log.info("平台代理到期重新分配：user={}, 原平台代理={}, 新城市代理={}",
                        userOpenid, expiredPlatformAgentOpenid, cityAgentOpenid);

            } catch (Exception e) {
                log.error("处理平台代理到期关系重新分配异常：user={}, expiredPlatformAgent={}",
                        userOpenid, expiredPlatformAgentOpenid, e);
            }
        }

        return processedCount;
    }

    /**
     * 处理个人代理到期
     * 个人代理到期，直接解除推广关系即可
     */
    private int handleExpiredPersonalAgent(String expiredPersonalAgentOpenid, List<Map<String, Object>> subordinateRelations) {
        log.info("处理个人代理到期：expiredPersonalAgent={}, subordinateCount={}",
                expiredPersonalAgentOpenid, subordinateRelations.size());

        int processedCount = 0;

        for (Map<String, Object> relation : subordinateRelations) {
            String userOpenid = (String) relation.get("user_openid");
            if (StrUtil.isBlank(userOpenid)) {
                continue;
            }

            try {
                // 个人代理到期，直接解除推广关系
                removePromotionRelationship(expiredPersonalAgentOpenid, userOpenid);

                processedCount++;
                log.info("个人代理到期解除关系：user={}, expiredPersonalAgent={}",
                        userOpenid, expiredPersonalAgentOpenid);

            } catch (Exception e) {
                log.error("处理个人代理到期关系解除异常：user={}, expiredPersonalAgent={}",
                        userOpenid, expiredPersonalAgentOpenid, e);
            }
        }

        return processedCount;
    }

    /**
     * 处理未知等级代理到期（默认处理方式）
     * 直接解除推广关系
     */
    private int handleExpiredDefaultAgent(String expiredAgentOpenid, List<Map<String, Object>> subordinateRelations) {
        log.info("处理未知等级代理到期：expiredAgent={}, subordinateCount={}",
                expiredAgentOpenid, subordinateRelations.size());

        int processedCount = 0;

        for (Map<String, Object> relation : subordinateRelations) {
            String userOpenid = (String) relation.get("user_openid");
            if (StrUtil.isBlank(userOpenid)) {
                continue;
            }

            try {
                // 默认处理：直接解除推广关系
                removePromotionRelationship(expiredAgentOpenid, userOpenid);

                processedCount++;
                log.info("默认代理到期解除关系：user={}, expiredAgent={}",
                        userOpenid, expiredAgentOpenid);

            } catch (Exception e) {
                log.error("处理默认代理到期关系解除异常：user={}, expiredAgent={}",
                        userOpenid, expiredAgentOpenid, e);
            }
        }

        return processedCount;
    }
}
