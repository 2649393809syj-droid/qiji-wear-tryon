package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.entities.AgentResponse;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 管理端代理审核服务类
 * 
 * @author system
 */
@Service
public class AdminAgentService {

    private static final Logger log = LoggerFactory.getLogger(AdminAgentService.class);

    @Autowired
    private AgentPromotionLogService agentPromotionLogService;

    /**
     * 获取代理列表
     * 
     * @param status   状态筛选，可选，null表示查询全部
     * @param role     角色筛选，可选，null表示查询全部
     * @param page     页码，从1开始
     * @param pageSize 每页大小
     * @return
     */
    public AgentResponse getAgentList(Integer status, Integer role, Integer page, Integer pageSize) {
        try {
            log.info("开始获取代理列表：status={}, role={}, page={}, pageSize={}", status, role, page, pageSize);

            // 构建查询参数对象，__mysql_fieldkeys__和__mysql_values__只传4个参数，pageSize/offset单独set
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("status");
            fieldKeys.add("status");
            fieldKeys.add("role");
            fieldKeys.add("role");

            JSONArray values = new JSONArray();
            values.add(status != null ? status : null);
            values.add(status != null ? status : null);
            values.add(role != null ? role : null);
            values.add(role != null ? role : null);

            JSONObject paramObj = new JSONObject();
            paramObj.set("__mysql_fieldkeys__", fieldKeys);
            paramObj.set("__mysql_values__", values);
            paramObj.set("pageSize", pageSize != null ? pageSize : 10);
            paramObj.set("offset", page != null ? (page - 1) * (pageSize != null ? pageSize : 10) : 0);

            log.info("查询参数：{}", paramObj);

            // 获取代理列表
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4021_ADMIN_GET_AGENT_LIST,
                    "F_301_4021_ADMIN_GET_AGENT_LIST",
                    "qj_marketing_v2_agents",
                    paramObj,
                    new HashSet<>());

            log.info("查询结果：{}", result.toString());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("获取代理列表失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) result.get("result");

            // 获取总数量，统计参数也传4个
            JSONArray countFieldKeys = new JSONArray();
            countFieldKeys.add("status");
            countFieldKeys.add("status");
            countFieldKeys.add("role");
            countFieldKeys.add("role");
            JSONArray countValues = new JSONArray();
            countValues.add(status != null ? status : null);
            countValues.add(status != null ? status : null);
            countValues.add(role != null ? role : null);
            countValues.add(role != null ? role : null);
            JSONObject countParam = new JSONObject();
            countParam.set("__mysql_fieldkeys__", countFieldKeys);
            countParam.set("__mysql_values__", countValues);
            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4026_ADMIN_GET_AGENT_COUNT,
                    "F_301_4026_ADMIN_GET_AGENT_COUNT",
                    "qj_marketing_v2_agents",
                    countParam,
                    new HashSet<>());

            log.info("统计结果：{}", countResult.toString());

            if (countResult.getInt("errCode") != 0) {
                return AgentResponse.fail("获取代理总数失败：" + countResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> countData = (List<Map<String, Object>>) countResult.get("result");
            int totalCount = Convert.toInt(countData.get(0).get("total"), 0);

            JSONObject responseData = new JSONObject();
            responseData.set("list", agentList);
            responseData.set("totalCount", totalCount);
            responseData.set("page", page != null ? page : 1);
            responseData.set("pageSize", pageSize != null ? pageSize : 10);

            log.info("返回数据：{}", responseData.toString());

            return AgentResponse.success("获取代理列表成功", responseData);
        } catch (Exception e) {
            log.error("获取代理列表失败", e);
            return AgentResponse.fail("获取代理列表异常：" + e.getMessage());
        }
    }

    /**
     * 审核通过代理申请
     * 
     * @param openid 用户openid
     * @return
     */
    public AgentResponse approveAgent(String openid, int currentPrice) {
        log.info("approveAgent: 入参 currentPrice(分)={}", currentPrice);
        log.info("approveAgent: step0-方法进入, openid={}", openid);
        if (StrUtil.isBlank(openid)) {
            log.info("approveAgent: step1-参数校验失败, openid为空");
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            Integer behaviorPrice = getLatestAgentPaymentAmount(openid);
            log.info("behaviorPrice的查询数据----", behaviorPrice);
            if (behaviorPrice != null) {
                log.info("approveAgent: 从用户行为记录获取currentPrice(分)={},覆盖入参", behaviorPrice);
                currentPrice = behaviorPrice;
            }

            log.info("approveAgent: step2-开始审核通过代理申请：openid={}", openid);

            // 计算过期时间（当前时间+365天）
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 365);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String expiredDate = sdf.format(calendar.getTime());
            log.info("approveAgent: step3-过期时间已计算, expiredDate={}", expiredDate);

            // 1. 获取代理信息，先获取apply_role
            JSONObject queryParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray queryFieldKeys = new JSONArray();
            queryFieldKeys.add("openid");

            queryParam.set("__mysql_fieldkeys__", queryFieldKeys);
            queryParam.set("openid", openid);

            log.info("approveAgent: step7-准备获取代理信息, queryParam={}", queryParam.toString());
            JSONObject queryResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4024_ADMIN_GET_AGENT_INFO,
                    "F_301_4024_ADMIN_GET_AGENT_INFO",
                    "qj_marketing_v2_agents",
                    queryParam,
                    new HashSet<>());
            log.info("approveAgent: step8-获取代理信息结果, queryResult={}", queryResult.toString());

            if (queryResult.getInt("errCode") != 0) {
                log.info("approveAgent: step9-获取代理信息失败, errMsg={}", queryResult.getStr("errMsg"));
                return AgentResponse.fail("获取代理信息失败：" + queryResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentInfo = (List<Map<String, Object>>) queryResult.get("result");
            if (agentInfo == null || agentInfo.isEmpty()) {
                log.info("approveAgent: step10-未找到代理信息");
                return AgentResponse.fail("未找到代理信息");
            }

            // 新增：审批前apply_role与current_role判断逻辑
            Integer applyRole = null;
            Integer currentRole = null;
            try {
                applyRole = agentInfo.get(0).get("apply_role") != null
                        ? Integer.parseInt(agentInfo.get(0).get("apply_role").toString())
                        : null;
                currentRole = agentInfo.get(0).get("current_role") != null
                        ? Integer.parseInt(agentInfo.get(0).get("current_role").toString())
                        : null;
            } catch (Exception e) {
                log.warn("approveAgent: 解析apply_role或current_role失败: {}", e.getMessage());
            }
            if (applyRole != null && currentRole != null && currentRole != 0) {
                if (currentRole <= applyRole) {
                    log.info("approveAgent: 当前身份current_role={}，申请身份apply_role={}，不允许降级或平级申请！", currentRole, applyRole);
                    return AgentResponse.fail("当前身份不允许降级或平级申请代理");
                }
            }

            // 2. 更新代理状态和current_role（一次性操作）
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("status");
            fieldKeys.add("expired_date");
            fieldKeys.add("current_role");
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("status", AgentStatusCodes.AGENT_STATUS_VALID);
            param.set("expired_date", expiredDate);
            param.set("current_role", applyRole != null ? applyRole : 0); // 直接设置current_role为apply_role
            param.set("openid", openid);

            log.info("approveAgent: step4-准备更新代理状态和current_role, param={}", param.toString());
            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4022_ADMIN_APPROVE_AGENT,
                    "F_301_4022_ADMIN_APPROVE_AGENT",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());
            log.info("approveAgent: step5-代理状态和current_role更新结果, result={}", result.toString());

            if (result.getInt("errCode") != 0) {
                log.info("approveAgent: step6-代理状态更新失败, errMsg={}", result.getStr("errMsg"));
                return AgentResponse.fail("审核通过失败：" + result.getStr("errMsg"));
            }

            // 3. 插入代理角色信息
            // 根据apply_role生成角色名称
            String roleName = getRoleNameByApplyRole(applyRole);
            int testTimes = getTestTimesByRole(applyRole);
            int testReportTimes = getTestReportTimesByRole(applyRole);

            // current_role已在上面的状态更新中一起完成，无需重复更新

            log.info(
                    "approveAgent: step11-准备插入代理角色信息: name={}, test_times={}, test_report_times={}, current_price(分)={}, renew_price={}",
                    roleName, testTimes, testReportTimes, currentPrice, 0);
            JSONObject roleParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray roleFieldKeys = new JSONArray();
            roleFieldKeys.add("name");
            roleFieldKeys.add("test_times");
            roleFieldKeys.add("test_report_times");
            roleFieldKeys.add("current_price");
            roleFieldKeys.add("renew_price");

            roleParam.set("__mysql_fieldkeys__", roleFieldKeys);
            roleParam.set("name", roleName);
            roleParam.set("test_times", testTimes);
            roleParam.set("test_report_times", testReportTimes);
            roleParam.set("current_price", currentPrice);
            roleParam.set("renew_price", 0);

            log.info("approveAgent: step12-插入代理角色SQL调用参数: {}", roleParam.toString());
            JSONObject insertResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4025_ADMIN_INSERT_AGENT_ROLE,
                    "F_301_4025_ADMIN_INSERT_AGENT_ROLE",
                    "qj_marketing_v2_agent_roles",
                    roleParam,
                    new HashSet<>());
            log.info("approveAgent: step13-插入代理角色SQL执行结果: {}", insertResult.toString());
            if (insertResult.getInt("errCode") != 0) {
                log.error("approveAgent: step14-插入代理角色信息失败：{}", insertResult.getStr("errMsg"));
                // 不影响主流程，继续返回成功
            }

            // 4. 记录代理审批推广关系变动日志（在推广关系调整之前记录）
            try {
                // 从查询结果中获取当前的代理等级（如果已经是代理的话）
                Integer actualCurrentRole = currentRole;

                log.info("调试日志记录 - 初始currentRole: {}", currentRole);
                log.info("调试日志记录 - agentInfo: {}", agentInfo);

                if (agentInfo != null && !agentInfo.isEmpty()) {
                    // 如果查询到了代理信息，说明已经是代理，获取当前等级
                    Object currentRoleObj = agentInfo.get(0).get("current_role");
                    log.info("调试日志记录 - 从数据库获取的current_role对象: {}", currentRoleObj);
                    log.info("调试日志记录 - current_role对象类型: {}", currentRoleObj != null ? currentRoleObj.getClass().getName() : "null");

                    if (currentRoleObj != null) {
                        actualCurrentRole = Convert.toInt(currentRoleObj);
                        log.info("从代理信息中获取到当前等级：{}", actualCurrentRole);
                    } else {
                        log.info("数据库中的current_role字段为null，使用默认值0");
                        actualCurrentRole = 0; // 如果数据库中current_role为null，说明是新用户，设置为0
                    }
                } else {
                    log.info("未查询到代理信息，使用默认值0");
                    actualCurrentRole = 0; // 如果没有查询到代理信息，说明是新用户，设置为0
                }

                log.info("最终用于日志记录的actualCurrentRole: {}", actualCurrentRole);
                agentPromotionLogService.recordPromotionLog(openid, applyRole, actualCurrentRole);
                log.info("代理审批推广关系变动日志记录完成：openid={}, 申请等级={}, 原等级={}", openid, applyRole, actualCurrentRole);
            } catch (Exception e) {
                log.error("代理审批推广关系变动日志记录异常：openid={}, 申请等级={}, 原等级={}", openid, applyRole, currentRole, e);
                // 日志记录失败不影响代理审批，只记录错误日志
            }

            // 5. 处理代理等级变更时的推广关系调整
            try {
                AgentPromotionRelationshipService relationshipService = new AgentPromotionRelationshipService();
                relationshipService.handleAgentLevelChange(openid, currentRole, applyRole);
                log.info("代理等级变更推广关系调整完成：openid={}, 原等级={}, 新等级={}", openid, currentRole, applyRole);
            } catch (Exception e) {
                log.error("代理等级变更推广关系调整异常：openid={}, 原等级={}, 新等级={}", openid, currentRole, applyRole, e);
                // 推广关系调整失败不影响代理审批，只记录日志
            }

            log.info("approveAgent: step15-代理申请审核通过成功：openid={}, expiredDate={}", openid, expiredDate);
            return AgentResponse.success("代理申请审核通过成功");

        } catch (Exception e) {
            log.error("approveAgent: step16-审核通过代理申请失败", e);
            return AgentResponse.fail("审核通过异常：" + e.getMessage());
        }
    }

    /**
     * 拒绝代理申请
     * 
     * @param openid 用户openid
     * @return
     */
    public AgentResponse rejectAgent(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.info("开始拒绝代理申请：openid={}", openid);

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("status");
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("status", AgentStatusCodes.AGENT_STATUS_REJECTED);
            param.set("openid", openid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4023_ADMIN_REJECT_AGENT,
                    "F_301_4023_ADMIN_REJECT_AGENT",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("拒绝申请失败：" + result.getStr("errMsg"));
            }

            log.info("代理申请拒绝成功：openid={}", openid);
            return AgentResponse.success("代理申请拒绝成功");

        } catch (Exception e) {
            log.error("拒绝代理申请失败", e);
            return AgentResponse.fail("拒绝申请异常：" + e.getMessage());
        }
    }

    /**
     * 暂停代理（status=4）
     * 
     * @param openid 用户openid
     * @return
     */
    public AgentResponse pauseAgent(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }
        try {
            log.info("pauseAgent: 开始暂停代理，openid={}", openid);

            // 1. 获取代理信息，用于日志记录
            Integer currentRole = null;
            List<String> affectedUserOpenids = new ArrayList<>();

            try {
                // 获取代理当前等级
                JSONObject agentInfo = getAgentInfo(openid);
                if (agentInfo != null) {
                    currentRole = Convert.toInt(agentInfo.get("current_role"));
                }

                // 获取受影响的用户列表
                affectedUserOpenids = getAffectedUserOpenids(openid);
            } catch (Exception e) {
                log.warn("获取代理信息用于日志记录失败：openid={}", openid, e);
            }

            // 2. 暂停代理
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("status");
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("status", AgentStatusCodes.AGENT_STATUS_SUSPENDED); // 4
            param.set("openid", openid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4006_AGENT_UPDATE_STATUS,
                    "F_301_4006_AGENT_UPDATE_STATUS",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("暂停失败：" + result.getStr("errMsg"));
            }

            // 3. 记录代理失去资格日志
            try {
                if (currentRole != null) {
                    String migrationDetails = affectedUserOpenids.isEmpty() ? "" : "下级用户推广关系暂时保留";
                    agentPromotionLogService.recordAgentExpiredLog(openid, currentRole, affectedUserOpenids, "管理员暂停代理", migrationDetails);
                }
            } catch (Exception e) {
                log.error("记录代理暂停日志失败：openid={}", openid, e);
            }

            log.info("pauseAgent: 代理已暂停，openid={}", openid);
            return AgentResponse.success("代理已暂停");

        } catch (Exception e) {
            log.error("pauseAgent: 暂停代理失败", e);
            return AgentResponse.fail("暂停异常：" + e.getMessage());
        }
    }

    /**
     * 取消代理资格（手动操作）
     * 取消代理资格并重新分配其下级的推广关系
     *
     * @param openid 用户openid
     * @return
     */
    public AgentResponse cancelAgent(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.info("开始取消代理资格：openid={}", openid);

            // 1. 获取代理信息
            JSONObject agentInfo = getAgentInfo(openid);
            if (agentInfo == null) {
                return AgentResponse.fail("未找到代理信息");
            }

            Integer currentRole = Convert.toInt(agentInfo.get("current_role"));
            Integer status = Convert.toInt(agentInfo.get("status"));

            log.info("代理信息：openid={}, currentRole={}, status={}", openid, currentRole, status);

            // 2. 获取该代理的上级代理openid（用于日志记录）
            String superiorAgentOpenid = getSuperiorAgentOpenid(openid);
            log.info("代理{}的上级代理：{}", openid, superiorAgentOpenid != null ? superiorAgentOpenid : "无");

            // 3. 获取该代理的下级推广关系
            List<Map<String, Object>> subordinateRelations = getSubordinateRelations(openid);
            log.info("代理{}有{}个下级推广关系", openid, subordinateRelations.size());

            // 4. 处理下级推广关系重新分配
            if (!subordinateRelations.isEmpty()) {
                try {
                    AgentPromotionRelationshipService relationshipService = new AgentPromotionRelationshipService();
                    int processedCount = relationshipService.handleExpiredAgentRelationships(openid, currentRole, subordinateRelations);
                    log.info("代理{}下级推广关系重新分配完成，处理数量：{}", openid, processedCount);
                } catch (Exception e) {
                    log.error("代理{}下级推广关系重新分配异常", openid, e);
                    return AgentResponse.fail("推广关系重新分配失败：" + e.getMessage());
                }
            }

            // 5. 解除代理自己与上级的推广关系
            try {
                boolean removedSuperiorRelation = removeCancelledAgentSuperiorRelationship(openid);
                if (removedSuperiorRelation) {
                    log.info("成功解除取消代理与上级的推广关系：代理openid={}", openid);
                } else {
                    log.info("取消代理没有上级推广关系或解除失败：代理openid={}", openid);
                }
            } catch (Exception e) {
                log.error("解除取消代理上级推广关系异常：代理openid={}", openid, e);
                // 不影响主流程，继续执行
            }

            // 6. 取消代理资格（设置状态为拒绝，清空current_role）
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("status");
            fieldKeys.add("current_role");
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("status", AgentStatusCodes.AGENT_STATUS_REJECTED); // 3 - 未通过
            param.set("current_role", 0); // 清空代理等级
            param.set("openid", openid);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4006_AGENT_UPDATE_STATUS,
                    "F_301_4006_AGENT_UPDATE_STATUS",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("取消代理资格失败：" + result.getStr("errMsg"));
            }

            // 7. 记录代理失去资格日志
            try {
                List<String> affectedUserOpenids = new ArrayList<>();
                for (Map<String, Object> relation : subordinateRelations) {
                    String userOpenid = (String) relation.get("user_openid");
                    if (StrUtil.isNotBlank(userOpenid)) {
                        affectedUserOpenids.add(userOpenid);
                    }
                }

                // 获取迁移详情
                String migrationDetails = getMigrationDetailsForCancelAgent(subordinateRelations, superiorAgentOpenid);
                agentPromotionLogService.recordAgentExpiredLog(openid, currentRole, affectedUserOpenids, "管理员取消代理资格", migrationDetails);
            } catch (Exception e) {
                log.error("记录代理取消资格日志失败：openid={}", openid, e);
            }

            log.info("代理资格取消成功：openid={}, 原等级={}, 下级重新分配数量={}",
                    openid, currentRole, subordinateRelations.size());
            return AgentResponse.success("代理资格取消成功，下级推广关系已重新分配");

        } catch (Exception e) {
            log.error("取消代理资格异常：openid={}", openid, e);
            return AgentResponse.fail("取消代理资格异常：" + e.getMessage());
        }
    }

    /**
     * 获取代理信息
     */
    private JSONObject getAgentInfo(String openid) {
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
                    return new JSONObject(agents.get(0));
                }
            }

            return null;
        } catch (Exception e) {
            log.error("获取代理信息异常：openid={}", openid, e);
            return null;
        }
    }

    /**
     * 获取代理的下级推广关系
     */
    private List<Map<String, Object>> getSubordinateRelations(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("agent_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("agent_openid", agentOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT,
                    "F_303_4204_AGENT_USER_GET_BY_AGENT",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                return relations != null ? relations : new ArrayList<>();
            }

            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取下级推广关系异常：agentOpenid={}", agentOpenid, e);
            return new ArrayList<>();
        }
    }

    private int getTestTimesByRole(Integer agentRole) {
        switch (agentRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT:
            case AgentStatusCodes.ROLE_PLATFORM_AGENT:
                return -1;
            case AgentStatusCodes.ROLE_PERSONAL_AGENT:
                return 100;
            default:
                return 0;
        }
    }

    private int getTestReportTimesByRole(Integer agentRole) {
        switch (agentRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT:
            case AgentStatusCodes.ROLE_PLATFORM_AGENT:
                return 20;
            case AgentStatusCodes.ROLE_PERSONAL_AGENT:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * 从用户行为记录中查询最近的代理申请支付金额
     * 暂时返回null，依赖Redis缓存机制
     */
    private Integer getLatestAgentPaymentAmount(String openid) {
        JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray fieldKeys = new JSONArray();
        fieldKeys.add("openid");
        param.set("__mysql_fieldkeys__", fieldKeys);
        param.set("openid", openid);

        JSONObject result = CRUDService.exeRetrieve(
                FunctionCodes.F_302_4101_USER_BEHAVIOR_LIST,
                "F_302_4101_USER_BEHAVIOR_LIST",
                "qj_marketing_v2_user_behavior",
                param,
                new HashSet<>());
        if (result.getInt("errCode") == 0) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("result");
            if (list != null && !list.isEmpty()) {
                Object amountValid = list.get(0).get("amount_valid");
                if (amountValid != null) {
                    if (amountValid instanceof Integer) {
                        return (Integer) amountValid;
                    } else if (amountValid instanceof Long) {
                        return ((Long) amountValid).intValue();
                    } else if (amountValid instanceof Double) {
                        return ((Double) amountValid).intValue();
                    } else if (amountValid instanceof String) {
                        return Integer.parseInt((String) amountValid);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取用户行为列表（管理端）
     * 
     * @param page     页码，从1开始
     * @param pageSize 每页大小
     * @return AgentResponse
     */
    public AgentResponse getUserBehaviorList(Integer page, Integer pageSize) {
        try {
            // 设置默认分页参数
            if (page == null) {
                page = 1;
            }
            if (pageSize == null) {
                pageSize = 10;
            }

            log.info("开始获取用户行为列表：page={}, pageSize={}", page, pageSize);

            // 构建查询参数对象
            JSONObject paramObj = new JSONObject();
            paramObj.set("pageSize", pageSize);
            paramObj.set("offset", (page - 1) * pageSize);

            log.info("查询参数：{}", paramObj);

            // 获取用户行为列表
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_302_4104_ADMIN_USER_BEHAVIOR_LIST,
                    "F_302_4104_ADMIN_USER_BEHAVIOR_LIST",
                    "qj_marketing_v2_user_behavior",
                    paramObj,
                    new HashSet<>());

            log.info("查询结果：{}", result.toString());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("获取用户行为列表失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> behaviorList = (List<Map<String, Object>>) result.get("result");

            // 获取总数量
            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_302_4105_ADMIN_USER_BEHAVIOR_COUNT,
                    "F_302_4105_ADMIN_USER_BEHAVIOR_COUNT",
                    "qj_marketing_v2_user_behavior",
                    new JSONObject(),
                    new HashSet<>());

            log.info("统计结果：{}", countResult.toString());

            if (countResult.getInt("errCode") != 0) {
                return AgentResponse.fail("获取用户行为总数失败：" + countResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> countData = (List<Map<String, Object>>) countResult.get("result");
            int totalCount = Convert.toInt(countData.get(0).get("total"), 0);

            JSONObject responseData = new JSONObject();
            responseData.set("list", behaviorList);
            responseData.set("totalCount", totalCount);
            responseData.set("page", page);
            responseData.set("pageSize", pageSize);

            log.info("返回数据：{}", responseData.toString());

            return AgentResponse.success("获取用户行为列表成功", responseData);
        } catch (Exception e) {
            log.error("获取用户行为列表失败", e);
            return AgentResponse.fail("获取用户行为列表异常：" + e.getMessage());
        }
    }

    /**
     * 获取推广关系列表（管理端）
     * 
     * @param page     页码，从1开始
     * @param pageSize 每页大小
     * @return AgentResponse
     */
    public AgentResponse getAgentUserRelationList(Integer page, Integer pageSize) {
        try {
            // 设置默认分页参数
            if (page == null) {
                page = 1;
            }
            if (pageSize == null) {
                pageSize = 10;
            }

            log.info("开始获取代理用户关系列表：page={}, pageSize={}", page, pageSize);

            // 构建查询参数对象
            JSONObject paramObj = new JSONObject();
            paramObj.set("pageSize", pageSize);
            paramObj.set("offset", (page - 1) * pageSize);

            log.info("查询参数：{}", paramObj);

            // 获取代理用户关系列表
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4205_ADMIN_AGENT_USER_LIST,
                    "F_303_4205_ADMIN_AGENT_USER_LIST",
                    "qj_marketing_v2_agent_users",
                    paramObj,
                    new HashSet<>());

            log.info("查询结果：{}", result.toString());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("获取代理用户关系列表失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationList = (List<Map<String, Object>>) result.get("result");

            // 获取总数量
            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4206_ADMIN_AGENT_USER_COUNT,
                    "F_303_4206_ADMIN_AGENT_USER_COUNT",
                    "qj_marketing_v2_agent_users",
                    new JSONObject(),
                    new HashSet<>());

            log.info("统计结果：{}", countResult.toString());

            if (countResult.getInt("errCode") != 0) {
                return AgentResponse.fail("获取代理用户关系总数失败：" + countResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> countData = (List<Map<String, Object>>) countResult.get("result");
            int totalCount = Convert.toInt(countData.get(0).get("total"), 0);

            JSONObject responseData = new JSONObject();
            responseData.set("list", relationList);
            responseData.set("totalCount", totalCount);
            responseData.set("page", page);
            responseData.set("pageSize", pageSize);

            log.info("返回数据：{}", responseData.toString());

            return AgentResponse.success("获取推广关系列表成功", responseData);
        } catch (Exception e) {
            log.error("获取代理用户关系列表失败", e);
            return AgentResponse.fail("获取推广关系列表异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取用户昵称
     * 
     * @param openid 用户openid
     * @return 用户昵称，未找到返回null
     */
    private String getUserNickname(String openid) {
        try {
            if (StrUtil.isBlank(openid)) {
                return null;
            }

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("_openid");
            fieldKeys.add("is_del");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("_openid", openid);
            param.set("is_del", 0); // 查询未删除的用户

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2012_MEMBER_GET_NICKNAME,
                    "F_101_2012_MEMBER_GET_NICKNAME",
                    "qj_member",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> memberList = (List<Map<String, Object>>) result.get("result");

                if (memberList != null && !memberList.isEmpty()) {
                    Object nickname = memberList.get(0).get("nick_name");
                    return nickname != null ? nickname.toString() : null;
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("获取用户昵称失败：openid={}", openid, e);
            return null;
        }
    }

    /**
     * Controller参数转发用：getUserBehaviorList
     */
    public JSONObject getUserBehaviorListService(String sBody) {
        JSONObject result = new JSONObject();
        try {
            JSONObject params = cn.hutool.json.JSONUtil.parseObj(sBody);
            Integer page = params.getInt("page");
            Integer pageSize = params.getInt("pageSize");
            AgentResponse response = this.getUserBehaviorList(page, pageSize);
            result.set("success", response.isSuccess());
            result.set("message", response.getMessage());
            result.set("data", response.getData());
            result.set("errCode", response.isSuccess() ? 0 : 500);
            result.set("errMsg", response.getMessage());
        } catch (Exception e) {
            log.error("获取用户行为列表失败", e);
            result.set("success", false);
            result.set("message", "获取用户行为列表异常：" + e.getMessage());
            result.set("errCode", 500);
            result.set("errMsg", e.getMessage());
        }
        return result;
    }

    /**
     * Controller参数转发用：getAgentUserRelationList
     */
    public JSONObject getAgentUserRelationListService(String sBody) {
        JSONObject result = new JSONObject();
        try {
            JSONObject params = cn.hutool.json.JSONUtil.parseObj(sBody);
            Integer page = params.getInt("page");
            Integer pageSize = params.getInt("pageSize");
            AgentResponse response = this.getAgentUserRelationList(page, pageSize);
            result.set("success", response.isSuccess());
            result.set("message", response.getMessage());
            result.set("data", response.getData());
            result.set("errCode", response.isSuccess() ? 0 : 500);
            result.set("errMsg", response.getMessage());
        } catch (Exception e) {
            log.error("获取推广关系列表失败", e);
            result.set("success", false);
            result.set("message", "获取推广关系列表异常：" + e.getMessage());
            result.set("errCode", 500);
            result.set("errMsg", e.getMessage());
        }
        return result;
    }

    /**
     * 解除取消代理与上级的推广关系
     */
    private boolean removeCancelledAgentSuperiorRelationship(String cancelledAgentOpenid) {
        try {
            // 查询取消代理的上级推广关系
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", cancelledAgentOpenid);

            JSONObject queryResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (queryResult.getInt("errCode") != 0) {
                log.error("查询取消代理上级关系失败：cancelledAgent={}, error={}",
                        cancelledAgentOpenid, queryResult.getStr("errMsg"));
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relations = (List<Map<String, Object>>) queryResult.get("result");
            if (relations == null || relations.isEmpty()) {
                log.info("取消代理没有上级推广关系：cancelledAgent={}", cancelledAgentOpenid);
                return true; // 没有关系也算成功
            }

            // 解除与上级的推广关系
            for (Map<String, Object> relation : relations) {
                String agentOpenid = (String) relation.get("agent_openid");
                if (StrUtil.isNotBlank(agentOpenid)) {
                    boolean removed = removeSuperiorPromotionRelationship(cancelledAgentOpenid, agentOpenid);
                    if (removed) {
                        log.info("成功解除取消代理与上级的推广关系：cancelledAgent={}, superiorAgent={}",
                                cancelledAgentOpenid, agentOpenid);
                    } else {
                        log.error("解除取消代理与上级的推广关系失败：cancelledAgent={}, superiorAgent={}",
                                cancelledAgentOpenid, agentOpenid);
                        return false;
                    }
                }
            }

            return true;

        } catch (Exception e) {
            log.error("解除取消代理上级推广关系异常：cancelledAgent={}", cancelledAgentOpenid, e);
            return false;
        }
    }

    /**
     * 解除推广关系（用于取消代理）
     */
    private boolean removeSuperiorPromotionRelationship(String userOpenid, String agentOpenid) {
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
                log.info("成功解除推广关系：user={}, agent={}", userOpenid, agentOpenid);
                return true;
            } else {
                log.error("解除推广关系失败：user={}, agent={}, error={}",
                        userOpenid, agentOpenid, result.getStr("errMsg"));
                return false;
            }

        } catch (Exception e) {
            log.error("解除推广关系异常：user={}, agent={}", userOpenid, agentOpenid, e);
            return false;
        }
    }

    /**
     * 管理端添加代理
     *
     * @param openid 代理openid
     * @param applyRole 代理等级
     * @param city 城市（城市代理必填）
     * @return 添加结果
     */
    public AgentResponse addAgent(String openid, Integer applyRole, String city) {
        try {
            log.info("addAgent: step1-开始添加代理：openid={}, currentRole={}, city={}", openid, applyRole, city);

            // 1. 参数验证
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("代理openid不能为空");
            }
            if (applyRole == null) {
                return AgentResponse.fail("代理等级不能为空");
            }
            if (!isValidRole(applyRole)) {
                return AgentResponse.fail("无效的代理等级");
            }
            if (applyRole == AgentStatusCodes.ROLE_CITY_AGENT && StrUtil.isBlank(city)) {
                return AgentResponse.fail("城市代理必须选择城市");
            }

            // 2. 检查代理等级限制
            AgentResponse levelCheckResult = checkAgentLevelRestriction(openid, applyRole);
            if (levelCheckResult != null) {
                return levelCheckResult;
            }

            // 3. 获取用户昵称
            String nickname = getUserNickname(openid);
            if (StrUtil.isBlank(nickname)) {
                log.warn("addAgent: 未找到用户昵称，openid={}", openid);
                nickname = "未知用户";
            }

            // 4. 根据代理等级获取配置信息
            int testTimes = getTestTimesByRole(applyRole);
            int testReportTimes = getTestReportTimesByRole(applyRole);

            // 5. 构建插入参数
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            fieldKeys.add("openid");
            fieldKeys.add("name");
            fieldKeys.add("status");
            fieldKeys.add("apply_role");
            fieldKeys.add("current_role");
            fieldKeys.add("test_times");
            fieldKeys.add("test_report_times");
            fieldKeys.add("city");
            fieldKeys.add("join_date");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);
            param.set("name", nickname);
            param.set("status", 2); // 默认未审批状态
            param.set("apply_role", applyRole);
            param.set("current_role", 0); // 当前角色为0，需要审批后才生效
            param.set("test_times", testTimes);
            param.set("test_report_times", testReportTimes);
            param.set("city", city);
            param.set("join_date", new Date());

            log.info("addAgent: step2-准备插入代理信息, param={}", param.toString());

            // 6. 执行插入
            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4028_ADMIN_ADD_AGENT,
                    "F_301_4028_ADMIN_ADD_AGENT",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            log.info("addAgent: step3-插入代理信息结果, result={}", result.toString());

            if (result.getInt("errCode") == 0) {
                log.info("addAgent: step4-代理添加成功：openid={}, currentRole={}", openid, applyRole);
                return AgentResponse.success("代理添加成功，等待审批");
            } else {
                log.error("addAgent: step5-代理添加失败：{}", result.getStr("errMsg"));
                return AgentResponse.fail("代理添加失败：" + result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("addAgent: step6-添加代理异常", e);
            return AgentResponse.fail("添加代理异常：" + e.getMessage());
        }
    }

    /**
     * 验证代理等级是否有效
     */
    private boolean isValidRole(Integer role) {
        return role == AgentStatusCodes.ROLE_CITY_AGENT ||
               role == AgentStatusCodes.ROLE_PLATFORM_AGENT ||
               role == AgentStatusCodes.ROLE_PERSONAL_AGENT;
    }

    /**
     * 根据apply_role获取角色名称
     */
    private String getRoleNameByApplyRole(Integer applyRole) {
        if (applyRole == null) {
            return "未知角色";
        }

        switch (applyRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT:
                return "城市代理";
            case AgentStatusCodes.ROLE_PLATFORM_AGENT:
                return "平台代理";
            case AgentStatusCodes.ROLE_PERSONAL_AGENT:
                return "个人代理";
            default:
                return "未知角色";
        }
    }

    /**
     * 获取受影响的用户openid列表
     */
    private List<String> getAffectedUserOpenids(String agentOpenid) {
        List<String> affectedUserOpenids = new ArrayList<>();
        try {
            List<Map<String, Object>> subordinateRelations = getSubordinateRelations(agentOpenid);
            for (Map<String, Object> relation : subordinateRelations) {
                String userOpenid = (String) relation.get("user_openid");
                if (StrUtil.isNotBlank(userOpenid)) {
                    affectedUserOpenids.add(userOpenid);
                }
            }
        } catch (Exception e) {
            log.error("获取受影响用户列表异常：agentOpenid={}", agentOpenid, e);
        }
        return affectedUserOpenids;
    }

    /**
     * 获取取消代理资格时的迁移详情
     */
    private String getMigrationDetailsForCancelAgent(List<Map<String, Object>> subordinateRelations, String superiorAgentOpenid) {
        if (subordinateRelations == null || subordinateRelations.isEmpty()) {
            return "";
        }

        try {
            if (StrUtil.isNotBlank(superiorAgentOpenid)) {
                // 有上级代理，下级用户迁移到上级
                Integer superiorRole = getAgentRole(superiorAgentOpenid);
                String superiorRoleName = getRoleNameByRole(superiorRole);

                return String.format("下级用户迁移情况：%d个用户迁移到%s%s",
                        subordinateRelations.size(), superiorRoleName, superiorAgentOpenid);
            } else {
                // 没有上级代理，下级用户推广关系被解除
                return String.format("%d个用户推广关系被解除", subordinateRelations.size());
            }
        } catch (Exception e) {
            log.error("获取取消代理迁移详情异常", e);
            return "迁移详情获取失败";
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
     * 获取代理的上级代理openid
     */
    private String getSuperiorAgentOpenid(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", agentOpenid);

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
            log.error("获取上级代理异常：agentOpenid={}", agentOpenid, e);
            return null;
        }
    }

    /**
     * 检查代理等级限制
     * 不能插入相同等级的代理，也不能降级代理
     *
     * @param openid 用户openid
     * @param applyRole 申请的代理等级
     * @return 如果有限制返回错误响应，否则返回null表示可以添加
     */
    private AgentResponse checkAgentLevelRestriction(String openid, Integer applyRole) {
        try {
            // 1. 查询用户当前的代理信息
            JSONObject agentInfo = getAgentInfo(openid);

            if (agentInfo == null) {
                // 用户不是代理，可以添加任何等级
                log.info("用户{}不是代理，可以添加{}等级", openid, getRoleNameByApplyRole(applyRole));
                return null;
            }

            // 2. 获取用户当前的代理等级
            Integer currentRole = Convert.toInt(agentInfo.get("current_role"));
            Integer status = Convert.toInt(agentInfo.get("status"));

            log.info("用户{}当前代理信息：current_role={}, status={}, 申请等级={}",
                    openid, currentRole, status, applyRole);

            // 3. 检查代理状态
            if (status != null && status == AgentStatusCodes.AGENT_STATUS_VALID) {
                // 用户是有效代理，检查等级限制
                if (currentRole != null && currentRole != 0) {
                    if (currentRole.equals(applyRole)) {
                        // 相同等级，不允许
                        return AgentResponse.fail(String.format("用户已经是%s，不能重复添加相同等级",
                                getRoleNameByApplyRole(currentRole)));
                    } else if (currentRole < applyRole) {
                        // 降级，不允许（数值越小等级越高）
                        return AgentResponse.fail(String.format("用户当前是%s，不能降级为%s",
                                getRoleNameByApplyRole(currentRole), getRoleNameByApplyRole(applyRole)));
                    } else {
                        // 升级，允许
                        log.info("用户{}从{}升级为{}，允许添加",
                                openid, getRoleNameByApplyRole(currentRole), getRoleNameByApplyRole(applyRole));
                        return null;
                    }
                } else {
                    // current_role为0或null，但status为有效，可能是待审批状态，允许添加
                    log.info("用户{}是有效代理但current_role为{}，允许添加{}等级",
                            openid, currentRole, getRoleNameByApplyRole(applyRole));
                    return null;
                }
            } else if (status != null && (status == AgentStatusCodes.AGENT_STATUS_PENDING ||
                                        status == AgentStatusCodes.AGENT_STATUS_REJECTED)) {
                // 用户是待审批或被拒绝的代理
                Integer applyRoleInDb = Convert.toInt(agentInfo.get("apply_role"));

                if (applyRoleInDb != null && applyRoleInDb.equals(applyRole)) {
                    // 申请相同等级，不允许
                    return AgentResponse.fail(String.format("用户已有%s的申请记录（状态：%s），不能重复申请",
                            getRoleNameByApplyRole(applyRoleInDb), getStatusName(status)));
                } else if (applyRoleInDb != null && applyRoleInDb < applyRole) {
                    // 从高等级申请降级到低等级，不允许
                    return AgentResponse.fail(String.format("用户已申请%s（状态：%s），不能降级申请%s",
                            getRoleNameByApplyRole(applyRoleInDb), getStatusName(status), getRoleNameByApplyRole(applyRole)));
                } else {
                    // 升级申请，允许（会覆盖原申请）
                    log.info("用户{}从申请{}升级为申请{}，允许添加（将覆盖原申请）",
                            openid, getRoleNameByApplyRole(applyRoleInDb), getRoleNameByApplyRole(applyRole));
                    return null;
                }
            } else {
                // 其他状态（如暂停等），允许添加
                log.info("用户{}代理状态为{}，允许添加{}等级",
                        openid, status, getRoleNameByApplyRole(applyRole));
                return null;
            }

        } catch (Exception e) {
            log.error("检查代理等级限制异常：openid={}, applyRole={}", openid, applyRole, e);
            return AgentResponse.fail("检查代理等级限制失败：" + e.getMessage());
        }
    }

    /**
     * 添加临时代理
     *
     * @param openid 用户openid
     * @param agentRole 代理等级 (5=城市代理, 6=平台代理, 7=个人代理)
     * @param validDays 有效天数
     * @return AgentResponse
     */
    public AgentResponse addTemporaryAgent(String openid, Integer agentRole, Integer validDays,String city) {
        log.info("addTemporaryAgent: 开始添加临时代理, openid={}, agentRole={}, validDays={}", openid, agentRole, validDays);

        // 1. 参数校验
        if (StrUtil.isBlank(openid)) {
            log.warn("addTemporaryAgent: openid为空");
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        if (agentRole == null || (agentRole != AgentStatusCodes.ROLE_CITY_AGENT &&
                                  agentRole != AgentStatusCodes.ROLE_PLATFORM_AGENT &&
                                  agentRole != AgentStatusCodes.ROLE_PERSONAL_AGENT)) {
            log.warn("addTemporaryAgent: 代理等级无效, agentRole={}", agentRole);
            return AgentResponse.fail("参数错误：代理等级无效，必须为5(城市代理)、6(平台代理)或7(个人代理)");
        }

        if (validDays == null || validDays <= 0) {
            log.warn("addTemporaryAgent: 有效天数无效, validDays={}", validDays);
            return AgentResponse.fail("参数错误：有效天数必须大于0");
        }

        try {
            // 2. 检查用户是否已经是代理
            JSONObject checkParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray checkFieldKeys = new JSONArray();
            checkFieldKeys.add("openid");

            checkParam.set("__mysql_fieldkeys__", checkFieldKeys);
            checkParam.set("openid", openid);

            log.info("addTemporaryAgent: 检查用户是否已存在, checkParam={}", checkParam.toString());
            JSONObject checkResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4024_ADMIN_GET_AGENT_INFO,
                    "F_301_4024_ADMIN_GET_AGENT_INFO",
                    "qj_marketing_v2_agents",
                    checkParam,
                    new HashSet<>());

            log.info("addTemporaryAgent: 检查结果, checkResult={}", checkResult.toString());

            if (checkResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> existingAgent = (List<Map<String, Object>>) checkResult.get("result");
                if (existingAgent != null && !existingAgent.isEmpty()) {
                    log.warn("addTemporaryAgent: 用户已存在代理记录, openid={}", openid);
                    return AgentResponse.fail("该用户已存在代理记录，请先删除现有记录或使用审批功能");
                }
            }

            // 3. 计算过期时间
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, validDays);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String expiredDate = sdf.format(calendar.getTime());
            String joinDate = sdf.format(new Date());

            log.info("addTemporaryAgent: 计算时间, joinDate={}, expiredDate={}", joinDate, expiredDate);

            // 4. 插入临时代理记录
            JSONObject insertParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray insertFieldKeys = new JSONArray();
            insertFieldKeys.add("openid");
            insertFieldKeys.add("name");
            insertFieldKeys.add("apply_role");
            insertFieldKeys.add("current_role");
            insertFieldKeys.add("status");
            insertFieldKeys.add("join_date");
            insertFieldKeys.add("expired_date");
            insertFieldKeys.add("test_times");
            insertFieldKeys.add("test_report_times");
            insertFieldKeys.add("city");
            insertFieldKeys.add("from_openid");

            insertParam.set("__mysql_fieldkeys__", insertFieldKeys);
            insertParam.set("openid", openid);
            insertParam.set("name", "临时代理_" + openid.substring(0, Math.min(8, openid.length())));
            insertParam.set("apply_role", agentRole);
            insertParam.set("current_role", agentRole); // 临时代理直接设置当前角色
            insertParam.set("status", AgentStatusCodes.AGENT_STATUS_VALID); // 直接设为有效状态
            insertParam.set("join_date", joinDate);
            insertParam.set("city", city);
            insertParam.set("expired_date", expiredDate);

            // 根据代理等级设置测试次数
            int testTimes = getTestTimesByRole(agentRole);
            int testReportTimes = getTestReportTimesByRole(agentRole);
            insertParam.set("test_times", testTimes);
            insertParam.set("test_report_times", testReportTimes);
            insertParam.set("from_openid", null); // 临时代理不设置推荐人

            log.info("addTemporaryAgent: 准备插入临时代理, insertParam={}", insertParam.toString());
            JSONObject insertResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4004_AGENT_CREATE,
                    "F_301_4004_AGENT_CREATE",
                    "qj_marketing_v2_agents",
                    insertParam,
                    new HashSet<>());

            log.info("addTemporaryAgent: 插入结果, insertResult={}", insertResult.toString());

            if (insertResult.getInt("errCode") != 0) {
                log.error("addTemporaryAgent: 插入临时代理失败, errMsg={}", insertResult.getStr("errMsg"));
                return AgentResponse.fail("添加临时代理失败：" + insertResult.getStr("errMsg"));
            }

            // 5. 插入代理角色信息（可选，用于记录）
            try {
                String roleName = getRoleNameByRole(agentRole);
                insertAgentRoleRecord(roleName, testTimes, testReportTimes, 0); // 临时代理价格为0
                log.info("addTemporaryAgent: 代理角色记录插入成功");
            } catch (Exception e) {
                log.warn("addTemporaryAgent: 插入代理角色记录失败，但不影响主流程", e);
            }

            String roleDesc = AgentStatusCodes.getRoleDescription(agentRole);
            log.info("addTemporaryAgent: 临时代理添加成功, openid={}, role={}, validDays={}", openid, roleDesc, validDays);
            return AgentResponse.success(String.format("临时代理添加成功：%s，有效期%d天", roleDesc, validDays));

        } catch (Exception e) {
            log.error("addTemporaryAgent: 添加临时代理异常", e);
            return AgentResponse.fail("添加临时代理异常：" + e.getMessage());
        }
    }



    /**
     * 插入代理角色记录
     */
    private void insertAgentRoleRecord(String roleName, int testTimes, int testReportTimes, int currentPrice) {
        try {
            JSONObject roleParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray roleFieldKeys = new JSONArray();
            roleFieldKeys.add("name");
            roleFieldKeys.add("test_times");
            roleFieldKeys.add("test_report_times");
            roleFieldKeys.add("current_price");
            roleFieldKeys.add("renew_price");

            roleParam.set("__mysql_fieldkeys__", roleFieldKeys);
            roleParam.set("name", roleName);
            roleParam.set("test_times", testTimes);
            roleParam.set("test_report_times", testReportTimes);
            roleParam.set("current_price", currentPrice);
            roleParam.set("renew_price", 0);

            JSONObject insertResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4025_ADMIN_INSERT_AGENT_ROLE,
                    "F_301_4025_ADMIN_INSERT_AGENT_ROLE",
                    "qj_marketing_v2_agent_roles",
                    roleParam,
                    new HashSet<>());

            if (insertResult.getInt("errCode") != 0) {
                log.warn("插入代理角色记录失败：{}", insertResult.getStr("errMsg"));
            }
        } catch (Exception e) {
            log.warn("插入代理角色记录异常", e);
        }
    }

    /**
     * 根据状态码获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }

        switch (status) {
            case AgentStatusCodes.AGENT_STATUS_VALID:
                return "有效";
            case AgentStatusCodes.AGENT_STATUS_PENDING:
                return "待审批";
            case AgentStatusCodes.AGENT_STATUS_REJECTED:
                return "已拒绝";
            case AgentStatusCodes.AGENT_STATUS_SUSPENDED:
                return "已暂停";
            default:
                return "未知状态(" + status + ")";
        }
    }
}