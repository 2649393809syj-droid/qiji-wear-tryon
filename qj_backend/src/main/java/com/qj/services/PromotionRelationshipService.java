package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.FunctionCodes;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 推广关系管理服务
 * 处理推广关系的时间限制和状态管理
 */
@Service
public class PromotionRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(PromotionRelationshipService.class);

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentPromotionLogService agentPromotionLogService;

    /**
     * 定时任务：每天凌晨2点执行推广关系检查
     * 1. 检查代理是否过期，将过期代理状态设为5（待续费）
     * 2. 检查临时推广关系是否超过3个月
     * 3. 检查状态为5的代理是否超过宽限期，执行清理任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkPromotionRelationships() {
        log.info("开始执行推广关系定时检查任务");

        try {
            // 1. 首先检查代理过期状态，将过期代理设为待续费状态
            processExpiredAgents();

            // 2. 处理超时的临时推广关系
            processExpiredTemporaryRelationships();

            // 3. 处理失效代理的推广关系（状态为5且超过宽限期的代理）
            processInvalidAgentRelationships();

            log.info("推广关系定时检查任务执行完成");

        } catch (Exception e) {
            log.error("推广关系定时检查任务执行异常", e);
        }
    }

    /**
     * 处理过期代理
     * 检查所有有效状态(status=1)的代理，将过期的代理状态更新为待续费(status=5)
     */
    private void processExpiredAgents() {
        try {
            log.info("开始处理过期代理状态更新");

            // 获取所有有效状态的代理
            JSONObject validAgentsResult = getValidAgents();
            if (validAgentsResult.getInt("errCode") != 0) {
                log.error("获取有效代理列表失败：{}", validAgentsResult.getStr("errMsg"));
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> validAgents = (List<Map<String, Object>>) validAgentsResult.get("result");
            if (validAgents == null || validAgents.isEmpty()) {
                log.info("没有找到有效状态的代理");
                return;
            }

            log.info("找到{}个有效状态的代理，开始检查过期时间", validAgents.size());

            int expiredCount = 0;
            Date now = new Date();

            for (Map<String, Object> agent : validAgents) {
                String openid = (String) agent.get("openid");
                Date expiredDate = (Date) agent.get("expired_date");
                Integer currentRole = Convert.toInt(agent.get("current_role"));
                String name = (String) agent.get("name");

                if (StrUtil.isBlank(openid) || expiredDate == null) {
                    log.warn("代理信息不完整，跳过：openid={}, expired_date={}", openid, expiredDate);
                    continue;
                }

                // 检查是否已过期
                if (now.after(expiredDate)) {
                    log.info("发现过期代理：openid={}, name={}, expired_date={}, current_role={}",
                            openid, name, DateUtil.formatDateTime(expiredDate), currentRole);

                    // 更新代理状态为待续费
                    boolean updated = updateAgentStatusToExpired(openid);
                    if (updated) {
                        expiredCount++;
                        log.info("代理状态已更新为待续费：openid={}, name={}", openid, name);
                    } else {
                        log.error("更新代理状态失败：openid={}, name={}", openid, name);
                    }
                }
            }

            log.info("过期代理状态更新完成，共处理{}个过期代理", expiredCount);

        } catch (Exception e) {
            log.error("处理过期代理状态更新异常", e);
        }
    }

    /**
     * 处理超时的推广关系
     * 检查建立超过3个月且用户仍未成为代理的推广关系，自动解除
     */
    private void processExpiredTemporaryRelationships() {
        try {
            log.info("开始处理超时的临时推广关系");
            
            // 获取所有推广关系
            JSONObject allRelationsResult = getAllPromotionRelationships();
            if (allRelationsResult.getInt("errCode") != 0) {
                log.error("获取推广关系列表失败：{}", allRelationsResult.getStr("errMsg"));
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relations = (List<Map<String, Object>>) allRelationsResult.get("result");
            if (relations == null || relations.isEmpty()) {
                log.info("没有找到推广关系记录");
                return;
            }

            Date now = new Date();
            int expiredCount = 0;

            for (Map<String, Object> relation : relations) {
                String userOpenid = (String) relation.get("user_openid");
                String agentOpenid = (String) relation.get("agent_openid");
                Object joinDateObj = relation.get("join_date");

                if (StrUtil.isBlank(userOpenid) || StrUtil.isBlank(agentOpenid) || joinDateObj == null) {
                    continue;
                }

                Date joinDate = Convert.toDate(joinDateObj);
                if (joinDate == null) {
                    continue;
                }

                // 检查是否超过3个月（90天）
                long daysDiff = DateUtil.betweenDay(joinDate, now, false);
                log.info("检查推广关系：用户openid={}，代理openid={}，建立时间={}，当前时间={}，天数差={}",
                        userOpenid, agentOpenid, DateUtil.formatDateTime(joinDate), DateUtil.formatDateTime(now), daysDiff);

                if (daysDiff >= 60) {
                    // 检查用户是否已成为代理
                    JSONObject userAgentInfo = agentService.getCurrentUserAgentInfo(userOpenid);
                    if (userAgentInfo == null) {
                        // 用户仍未成为代理，解除推广关系
                        boolean removed = removePromotionRelationship(userOpenid, agentOpenid);
                        if (removed) {
                            expiredCount++;
                            log.info("解除超时临时推广关系：用户openid={}，代理openid={}，建立时间={}，已超时{}天",
                                    userOpenid, agentOpenid, DateUtil.formatDateTime(joinDate), daysDiff);

                            // 记录普通用户超时未升级日志
                            try {
                                agentPromotionLogService.recordUserTimeoutLog(userOpenid, agentOpenid, daysDiff);
                            } catch (Exception e) {
                                log.error("记录普通用户超时未升级日志失败：user={}, agent={}", userOpenid, agentOpenid, e);
                            }
                        }
                    } else {
                        // 用户已成为代理，保持推广关系不变
                        log.info("用户已成为代理，保持推广关系：用户openid={}，代理openid={}",
                                userOpenid, agentOpenid);
                    }
                }
            }

            log.info("处理超时临时推广关系完成，共解除{}个关系", expiredCount);

        } catch (Exception e) {
            log.error("处理超时临时推广关系异常", e);
        }
    }

    /**
     * 处理失效代理的推广关系（第二阶段清理）
     * 检查状态为5（待续费）的代理：
     * - 如果过期时间超过2个月宽限期，删除代理数据并解除所有推广关系
     * - 如果在2个月宽限期内，保持推广关系不变
     *
     * 注意：这是第二阶段处理，代理在第一阶段已经被设为status=5
     */
    private void processInvalidAgentRelationships() {
        try {
            log.info("开始处理失效代理的推广关系");
            
            // 获取所有状态为5的平台代理和城市代理
            JSONObject invalidAgentsResult = getInvalidAgents();
            if (invalidAgentsResult.getInt("errCode") != 0) {
                log.error("获取失效代理列表失败：{}", invalidAgentsResult.getStr("errMsg"));
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> invalidAgents = (List<Map<String, Object>>) invalidAgentsResult.get("result");
            if (invalidAgents == null || invalidAgents.isEmpty()) {
                log.info("没有找到失效的代理");
                return;
            }

            log.info("找到{}个状态为5的代理，开始检查宽限期", invalidAgents.size());

            int totalRemovedCount = 0;

            for (Map<String, Object> agent : invalidAgents) {
                String agentOpenid = (String) agent.get("openid");
                Integer currentRole = Convert.toInt(agent.get("current_role"));
                Date expiredDate = (Date) agent.get("expired_date");

                log.info("检查代理：openid={}，current_role={}，expired_date={}",
                        agentOpenid, currentRole, expiredDate != null ? DateUtil.formatDateTime(expiredDate) : "null");

                if (StrUtil.isBlank(agentOpenid)) {
                    continue;
                }

                // 处理所有状态为5（待续费）的代理，不限制current_role
                // 因为代理过期后current_role可能被重置为0，但status仍为5
                log.info("处理待续费代理：openid={}，current_role={}，expired_date={}",
                        agentOpenid, currentRole, expiredDate != null ? DateUtil.formatDateTime(expiredDate) : "null");

                // 检查是否超过2个月宽限期
                if (expiredDate != null) {
                    Date now = new Date();
                    long daysDiff = DateUtil.betweenDay(expiredDate, now, false);

                    if (daysDiff < 60) { // 60天 = 2个月宽限期
                        log.info("代理{}状态为待续费，过期时间为{}，在2个月宽限期内（过期{}天），保持推广关系",
                                agentOpenid, DateUtil.formatDateTime(expiredDate), daysDiff);
                        continue;
                    }

                    log.info("代理{}状态为待续费，过期时间为{}，已超过2个月宽限期（过期{}天），将删除代理数据并解除推广关系",
                            agentOpenid, DateUtil.formatDateTime(expiredDate), daysDiff);
                } else {
                    log.warn("代理{}没有过期时间记录，跳过处理", agentOpenid);
                    continue;
                }

                // 获取该代理名下的所有推广关系
                JSONObject agentRelationsResult = getPromotionRelationshipsByAgent(agentOpenid);
                if (agentRelationsResult.getInt("errCode") != 0) {
                    log.error("获取代理{}的推广关系失败：{}", agentOpenid, agentRelationsResult.getStr("errMsg"));
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agentRelations = (List<Map<String, Object>>) agentRelationsResult.get("result");

                // 收集受影响的用户openid列表，用于日志记录
                List<String> affectedUserOpenids = new ArrayList<>();
                if (agentRelations != null && !agentRelations.isEmpty()) {
                    for (Map<String, Object> relation : agentRelations) {
                        String userOpenid = (String) relation.get("user_openid");
                        if (StrUtil.isNotBlank(userOpenid)) {
                            affectedUserOpenids.add(userOpenid);
                        }
                    }
                }

                int removedCount = 0;
                String migrationDetails = "";

                if (agentRelations != null && !agentRelations.isEmpty()) {
                    // 有推广关系，需要重新分配下级的推广关系
                    try {
                        AgentPromotionRelationshipService relationshipService = new AgentPromotionRelationshipService();
                        removedCount = relationshipService.handleExpiredAgentRelationships(agentOpenid, currentRole, agentRelations);
                        log.info("处理失效代理的推广关系重新分配：代理openid={}，代理等级={}，处理关系数量={}",
                                agentOpenid, currentRole, removedCount);

                        // 获取迁移详情
                        migrationDetails = getMigrationDetails(affectedUserOpenids, currentRole);

                    } catch (Exception e) {
                        log.error("处理失效代理推广关系重新分配异常：代理openid={}，代理等级={}", agentOpenid, currentRole, e);
                        // 如果重新分配失败，回退到简单解除关系的逻辑
                        for (Map<String, Object> relation : agentRelations) {
                            String userOpenid = (String) relation.get("user_openid");
                            if (StrUtil.isNotBlank(userOpenid)) {
                                boolean removed = removePromotionRelationship(userOpenid, agentOpenid);
                                if (removed) {
                                    removedCount++;
                                }
                            }
                        }
                        log.info("回退处理：解除失效代理的推广关系：代理openid={}，代理等级={}，解除关系数量={}",
                                agentOpenid, currentRole, removedCount);
                        migrationDetails = "下级用户推广关系已解除，未重新分配";
                    }
                } else {
                    log.info("失效代理没有推广关系：代理openid={}，代理等级={}",
                            agentOpenid, currentRole);
                }

                totalRemovedCount += removedCount;

                // 记录代理失去资格的日志
                try {
                    String reason = String.format("代理到期超过2个月宽限期（过期%d天）",
                            expiredDate != null ? DateUtil.betweenDay(expiredDate, new Date(), false) : 0);
                    agentPromotionLogService.recordAgentExpiredLog(agentOpenid, currentRole, affectedUserOpenids, reason, migrationDetails);
                } catch (Exception e) {
                    log.error("记录代理失去资格日志失败：agent={}", agentOpenid, e);
                }

                // 解除到期代理自己与上级的推广关系
                boolean removedSuperiorRelation = removeExpiredAgentSuperiorRelationship(agentOpenid);
                if (removedSuperiorRelation) {
                    log.info("成功解除到期代理与上级的推广关系：代理openid={}", agentOpenid);
                } else {
                    log.info("到期代理没有上级推广关系或解除失败：代理openid={}", agentOpenid);
                }

                // 无论是否有推广关系，都要删除代理数据
                boolean agentDeleted = deleteAgent(agentOpenid);
                if (agentDeleted) {
                    log.info("成功删除失效代理数据：代理openid={}", agentOpenid);
                } else {
                    log.error("删除失效代理数据失败：代理openid={}", agentOpenid);
                }
            }

            log.info("处理失效代理的推广关系完成，共解除{}个关系", totalRemovedCount);

        } catch (Exception e) {
            log.error("处理失效代理的推广关系异常", e);
        }
    }

    /**
     * 获取所有推广关系
     */
    private JSONObject getAllPromotionRelationships() {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            
            return CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4201_AGENT_USER_LIST,
                    "F_303_4201_AGENT_USER_LIST",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());
                    
        } catch (Exception e) {
            log.error("获取所有推广关系异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取推广关系异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取所有有效状态的代理（status=1）
     */
    private JSONObject getValidAgents() {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("status");
            values.add(1); // 状态为1（有效）

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            return CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4001_AGENT_LIST,
                    "F_301_4001_AGENT_LIST",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

        } catch (Exception e) {
            log.error("获取有效代理异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取有效代理异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 将代理状态更新为待续费
     */
    private boolean updateAgentStatusToExpired(String openid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("status");
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("status", 5); // 状态设为5（待续费）
            param.set("openid", openid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4006_AGENT_UPDATE_STATUS,
                    "F_301_4006_AGENT_UPDATE_STATUS",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("代理状态更新成功：openid={}, 新状态=待续费(5)", openid);
                return true;
            } else {
                log.error("代理状态更新失败：openid={}, errMsg={}", openid, result.getStr("errMsg"));
                return false;
            }

        } catch (Exception e) {
            log.error("更新代理状态异常：openid={}", openid, e);
            return false;
        }
    }

    /**
     * 获取失效的代理（状态为5的平台代理和城市代理）
     */
    private JSONObject getInvalidAgents() {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();
            
            fieldKeys.add("status");
            values.add(5); // 状态为5（待续费）
            
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);
            
            return CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4001_AGENT_LIST,
                    "F_301_4001_AGENT_LIST",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());
                    
        } catch (Exception e) {
            log.error("获取失效代理异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取失效代理异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取指定代理的所有推广关系
     */
    private JSONObject getPromotionRelationshipsByAgent(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();
            
            fieldKeys.add("agent_openid");
            values.add(agentOpenid);
            
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);
            
            return CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT,
                    "F_303_4204_AGENT_USER_GET_BY_AGENT",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());
                    
        } catch (Exception e) {
            log.error("获取代理推广关系异常，agentOpenid: {}", agentOpenid, e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取代理推广关系异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 解除到期代理与上级的推广关系
     */
    private boolean removeExpiredAgentSuperiorRelationship(String expiredAgentOpenid) {
        try {
            // 查询到期代理的上级推广关系
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", expiredAgentOpenid);

            JSONObject queryResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (queryResult.getInt("errCode") != 0) {
                log.error("查询到期代理上级关系失败：expiredAgent={}, error={}",
                        expiredAgentOpenid, queryResult.getStr("errMsg"));
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relations = (List<Map<String, Object>>) queryResult.get("result");
            if (relations == null || relations.isEmpty()) {
                log.info("到期代理没有上级推广关系：expiredAgent={}", expiredAgentOpenid);
                return true; // 没有关系也算成功
            }

            // 解除与上级的推广关系
            for (Map<String, Object> relation : relations) {
                String agentOpenid = (String) relation.get("agent_openid");
                if (StrUtil.isNotBlank(agentOpenid)) {
                    boolean removed = removePromotionRelationship(expiredAgentOpenid, agentOpenid);
                    if (removed) {
                        log.info("成功解除到期代理与上级的推广关系：expiredAgent={}, superiorAgent={}",
                                expiredAgentOpenid, agentOpenid);
                    } else {
                        log.error("解除到期代理与上级的推广关系失败：expiredAgent={}, superiorAgent={}",
                                expiredAgentOpenid, agentOpenid);
                        return false;
                    }
                }
            }

            return true;

        } catch (Exception e) {
            log.error("解除到期代理上级推广关系异常：expiredAgent={}", expiredAgentOpenid, e);
            return false;
        }
    }

    /**
     * 解除推广关系
     */
    private boolean removePromotionRelationship(String userOpenid, String agentOpenid) {
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
                log.info("成功解除推广关系：用户openid={}，代理openid={}", userOpenid, agentOpenid);
                return true;
            } else {
                log.error("解除推广关系失败：用户openid={}，代理openid={}，错误：{}",
                         userOpenid, agentOpenid, result.getStr("errMsg"));
                return false;
            }

        } catch (Exception e) {
            log.error("解除推广关系异常：用户openid={}，代理openid={}", userOpenid, agentOpenid, e);
            return false;
        }
    }



    /**
     * 手动触发推广关系检查（用于测试或紧急处理）
     */
    public JSONObject manualCheckPromotionRelationships() {
        try {
            log.info("手动触发推广关系检查");

            checkPromotionRelationships();

            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "推广关系检查完成");
            return result;

        } catch (Exception e) {
            log.error("手动推广关系检查异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "推广关系检查异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 删除代理数据
     */
    private boolean deleteAgent(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", agentOpenid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4009_AGENT_DELETE,
                    "F_301_4009_AGENT_DELETE",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                log.info("成功删除代理：openid={}", agentOpenid);
                return true;
            } else {
                log.error("删除代理失败：openid={}，错误：{}", agentOpenid, result.getStr("errMsg"));
                return false;
            }

        } catch (Exception e) {
            log.error("删除代理异常：openid={}", agentOpenid, e);
            return false;
        }
    }

    /**
     * 获取迁移详情描述
     *
     * @param affectedUserOpenids 受影响的用户openid列表
     * @param expiredAgentRole 失去资格的代理等级
     * @return 迁移详情描述
     */
    private String getMigrationDetails(List<String> affectedUserOpenids, Integer expiredAgentRole) {
        if (affectedUserOpenids == null || affectedUserOpenids.isEmpty()) {
            return "";
        }

        try {
            StringBuilder migrationInfo = new StringBuilder();
            int migratedCount = 0;
            int removedCount = 0;

            for (String userOpenid : affectedUserOpenids) {
                // 检查用户当前的推广关系状态
                String newSuperiorOpenid = getCurrentSuperiorOpenid(userOpenid);

                if (StrUtil.isNotBlank(newSuperiorOpenid)) {
                    // 用户已被迁移到新的上级
                    Integer newSuperiorRole = getAgentRole(newSuperiorOpenid);
                    String newSuperiorRoleName = getRoleNameByRole(newSuperiorRole);

                    if (migratedCount == 0) {
                        migrationInfo.append("关系都迁移到");
                    }

                    if (migratedCount > 0) {
                        migrationInfo.append("，");
                    }

                    migrationInfo.append(String.format("%s%s下级",
                             newSuperiorRoleName, newSuperiorOpenid));
                    migratedCount++;
                } else {
                    // 用户推广关系被解除
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                if (migratedCount > 0) {
                    migrationInfo.append(String.format("，%d个用户推广关系被解除", removedCount));
                } else {
                    migrationInfo.append(String.format("%d个用户推广关系被解除", removedCount));
                }
            }

            return migrationInfo.toString();

        } catch (Exception e) {
            log.error("获取迁移详情异常：affectedUsers={}", affectedUserOpenids, e);
            return "迁移详情获取失败";
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
     * 获取代理角色
     */
    private Integer getAgentRole(String agentOpenid) {
        try {
            if (StrUtil.isBlank(agentOpenid)) {
                return null;
            }

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", agentOpenid);

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
                    Object currentRole = agents.get(0).get("current_role");
                    return currentRole != null ? Convert.toInt(currentRole) : 0;
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("获取代理角色异常：agentOpenid={}", agentOpenid, e);
            return 0;
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
            case 5: // AgentStatusCodes.ROLE_CITY_AGENT
                return "城市代理";
            case 6: // AgentStatusCodes.ROLE_PLATFORM_AGENT
                return "平台代理";
            case 7: // AgentStatusCodes.ROLE_PERSONAL_AGENT
                return "个人代理";
            default:
                return "普通用户";
        }
    }

}