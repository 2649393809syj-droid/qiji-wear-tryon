package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.entities.Agent;
import com.qj.entities.AgentResponse;
import com.qj.entities.AgentRole;
import com.qj.entities.QrCodeResponse;
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
 * 代理服务类
 * 
 * @author system
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /**
     * 获取用户当前身份
     */
    public AgentResponse getUserIdentity(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.debug("开始获取用户身份，openid={}", openid);

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);

            log.debug("查询用户身份参数：{}", param.toString());

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            log.debug("查询用户身份结果：{}", result.toString());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("查询用户身份失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) result.get("result");

            if (agentList == null || agentList.isEmpty()) {
                // 普通用户
                JSONObject userResult = new JSONObject();
                userResult.set("identity", "普通用户");
                userResult.set("status", 0);
                userResult.set("roleId", 0);
                userResult.set("canApply", true);
                return AgentResponse.success("获取用户身份成功", userResult);
            } else {
                Map<String, Object> agentData = agentList.get(0);
                Agent agent = convertMapToAgent(agentData);

                JSONObject userResult = new JSONObject();
                Integer role = agent.getRole() != null ? agent.getRole() : 0;
                userResult.set("identity", AgentStatusCodes.getRoleDescription(role));
                userResult.set("status", agent.getStatus());
                userResult.set("statusDesc", AgentStatusCodes.getStatusDescription(agent.getStatus()));
                userResult.set("roleId", role);
                userResult.set("expiredDate", agent.getExpiredDate());
                userResult.set("canApply", agent.getStatus() == AgentStatusCodes.AGENT_STATUS_REJECTED ||
                        agent.getStatus() == AgentStatusCodes.AGENT_STATUS_RENEWAL);
                userResult.set("agent", agent);

                return AgentResponse.success("获取用户身份成功", userResult);
            }
        } catch (Exception e) {
            log.error("获取用户身份失败", e);
            return AgentResponse.fail("获取用户身份异常：" + e.getMessage());
        }
    }

    /**
     * 将Map转换为Agent对象
     */
    private Agent convertMapToAgent(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Agent agent = new Agent();
        agent.setId(Convert.toLong(data.get("id")))
                .setOpenid((String) data.get("openid"))
                .setName((String) data.get("name"))
                .setStatus(Convert.toInt(data.get("status")))
                .setExpiredDate((Date) data.get("expired_date"))
                // 优先用current_role字段赋值给role，兼容老数据
                .setRole(data.get("current_role") != null ? Convert.toInt(data.get("current_role"))
                        : Convert.toInt(data.get("role")))
                .setTestTimes(Convert.toInt(data.get("test_times")))
                .setTestReportTimes(Convert.toInt(data.get("test_report_times")))
                .setCity((String) data.get("city"))
                .setFromOpenid((String) data.get("from_openid"))
                .setJoinDate((Date) data.get("join_date"))
                .setCreateTime((Date) data.get("create_time"))
                .setUpdateTime((Date) data.get("update_time"));

        return agent;
    }

    /**
     * V2025版本处理代理申请支付成功
     */
    public AgentResponse handleAgentApplySuccess(String openid, Integer agentRole, String tradeNo,
            Integer be_type, String city, Integer currentPrice) {
        log.info("开始处理代理申请支付成功：openid={}, role={}, tradeNo={}, by_type={},city={}, currentPrice(分)={}",
                openid, agentRole, tradeNo, be_type, city, currentPrice);

        try {
            // 1. 参数验证
            if (StrUtil.isBlank(openid) || agentRole == null || StrUtil.isBlank(tradeNo)) {
                log.error("参数错误：openid={}, role={}, tradeNo={}", openid, agentRole, tradeNo);
                return AgentResponse.fail("参数错误");
            }

            // 2. 检查订单状态
            String existingOpenid = getOpenidByTradeNo(tradeNo);
            if (existingOpenid != null && !existingOpenid.equals(openid)) {
                log.error("订单归属错误：orderOpenid={}, requestOpenid={}", existingOpenid, openid);
                return AgentResponse.fail("订单信息错误");
            }

            // 3. 插入/更新订单记录
            if (!insertAgentOrderRecord(openid, tradeNo)) {
                log.error("插入订单记录失败：openid={}, tradeNo={}", openid, tradeNo);
                return AgentResponse.fail("创建订单记录失败");
            }

            // 4. 判断是续费还是申请
            boolean isRenewal = checkIfRenewal(openid, agentRole);
            log.info("用户{}操作类型判断：{}", openid, isRenewal ? "续费" : "申请");

            // 5. 插入代理记录
            if (!insertAgentRecord(openid, agentRole, city, isRenewal)) {
                log.error("插入代理记录失败：openid={}, role={}", openid, agentRole);
                // 这里可以考虑回滚订单记录
                return AgentResponse.fail("创建代理记录失败");
            }

            // 6. 如果是续费，插入agent_roles表记录
            if (isRenewal) {
                insertAgentRoleRecord(openid, agentRole, currentPrice);
            }

            // 7. 记录用户行为
            try {
                Long priceAmount = currentPrice != null ? currentPrice.longValue() : 1L;
                recordUserBehavior(tradeNo, openid, priceAmount, be_type);
            } catch (Exception e) {
                // 用户行为记录失败不影响主流程
                log.warn("记录用户行为失败：{}", e.getMessage());
            }

            log.info("代理{}处理成功：openid={}, role={}", isRenewal ? "续费" : "申请", openid, agentRole);
            return AgentResponse.success(isRenewal ? "代理续费处理成功" : "代理申请处理成功");
        } catch (Exception e) {
            log.error("处理代理申请异常", e);
            return AgentResponse.fail("处理代理申请异常：" + e.getMessage());
        }
    }

    /**
     * 判断是否为续费操作
     * 如果用户已有相同角色的有效代理记录，则认为是续费
     */
    private boolean checkIfRenewal(String openid, Integer agentRole) {
        try {
            JSONObject queryParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray queryFieldKeys = new JSONArray();
            queryFieldKeys.add("openid");
            queryParam.set("__mysql_fieldkeys__", queryFieldKeys);
            queryParam.set("openid", openid);

            JSONObject queryResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    queryParam,
                    new HashSet<>());

            if (queryResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agentList = (List<Map<String, Object>>) queryResult.get("result");
                if (agentList != null && !agentList.isEmpty()) {
                    Map<String, Object> latestRecord = agentList.get(0);
                    Integer currentRole = Convert.toInt(latestRecord.get("current_role"), 0);
                    Integer status = Convert.toInt(latestRecord.get("status"), 0);

                    // 如果当前角色与申请角色相同，且状态为有效(1)或待续费(5)，则认为是续费
                    if (currentRole.equals(agentRole) && (status == 1 || status == 5)) {
                        log.info("用户{}已有相同角色{}的代理记录，状态={}，判断为续费", openid, agentRole, status);
                        return true;
                    }
                }
            }

            log.info("用户{}没有相同角色{}的有效代理记录，判断为申请", openid, agentRole);
            return false;
        } catch (Exception e) {
            log.warn("判断续费状态异常：{}", e.getMessage());
            return false; // 异常时默认为申请
        }
    }

    /**
     * 插入代理角色记录到qj_marketing_v2_agent_roles表
     */
    private void insertAgentRoleRecord(String openid, Integer agentRole, Integer currentPrice) {
        try {
            log.info("开始插入代理角色记录：openid={}, role={}, currentPrice(分)={}", openid, agentRole, currentPrice);

            // 获取角色名称
            String roleName = getRoleNameByRole(agentRole);
            int testTimes = getTestTimesByRole(agentRole);
            int testReportTimes = getTestReportTimesByRole(agentRole);

            // 续费价格设置为当前支付价格
            int renewPrice = currentPrice != null ? currentPrice : 0;

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
            roleParam.set("current_price", currentPrice != null ? currentPrice : 0);
            roleParam.set("renew_price", renewPrice);

            log.info("插入代理角色SQL调用参数: {}", roleParam.toString());
            JSONObject insertResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_301_4025_ADMIN_INSERT_AGENT_ROLE,
                    "F_301_4025_ADMIN_INSERT_AGENT_ROLE",
                    "qj_marketing_v2_agent_roles",
                    roleParam,
                    new HashSet<>());

            log.info("插入代理角色SQL执行结果: {}", insertResult.toString());
            if (insertResult.getInt("errCode") != 0) {
                log.error("插入代理角色信息失败：{}", insertResult.getStr("errMsg"));
            } else {
                log.info("代理角色记录插入成功：openid={}, role={}", openid, agentRole);
            }
        } catch (Exception e) {
            log.error("插入代理角色记录异常：openid={}, role={}", openid, agentRole, e);
        }
    }

    /**
     * 根据角色ID获取角色名称
     */
    private String getRoleNameByRole(Integer agentRole) {
        switch (agentRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT: // 5
                return "城市代理";
            case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 6
                return "平台代理";
            case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 7
                return "个人代理";
            default:
                return "未知角色";
        }
    }

    /**
     * 创建订单
     */
    private String getOpenidByTradeNo(String tradeNo) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("trade_no");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("trade_no", tradeNo);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_201_3006_ORDER_GET_BY_TRADE_NO,
                    "F_201_3006_ORDER_GET_BY_TRADE_NO",
                    "qj_shop_order",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("result");
                if (orders != null && !orders.isEmpty()) {
                    return (String) orders.get(0).get("openid");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取订单openid失败", e);
            return null;
        }
    }

    /**
     * 记录用户行为
     */
    private void recordUserBehavior(String tradeNo, String openid, Long amount, Integer by_type) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");
            fieldKeys.add("be_type");
            fieldKeys.add("amount_valid");
            fieldKeys.add("be_date");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);
            param.set("be_type", by_type);
            param.set("amount_valid", amount);
            param.set("be_date", currentTime);

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_302_4103_USER_BEHAVIOR_CREATE,
                    "F_302_4103_USER_BEHAVIOR_CREATE",
                    "qj_marketing_v2_user_behavior",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("记录用户行为失败: {}", result.getStr("errMsg"));
            }
        } catch (Exception e) {
            log.error("记录用户行为失败", e);
        }
    }

    /**
     * 创建订单记录
     */
    private boolean insertAgentOrderRecord(String openid, String tradeNo) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");
            fieldKeys.add("order_time");
            fieldKeys.add("total");
            fieldKeys.add("amount_due");
            fieldKeys.add("trade_no");
            fieldKeys.add("pay_status");
            fieldKeys.add("type");
            fieldKeys.add("name");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);
            param.set("order_time", currentTime);
            param.set("total", 1L); // 测试阶段1分钱
            param.set("amount_due", 1L); // 测试阶段1分钱
            param.set("trade_no", tradeNo);
            param.set("pay_status", 1); // 已支付
            param.set("type", 10); // 代理申请订单
            param.set("name", "代理申请费用");

            log.debug("插入代理订单记录参数：{}", param.toString());
            log.debug("Function code: {}", FunctionCodes.F_201_3007_ORDER_CREATE);
            log.debug("Function name: F_201_3007_ORDER_CREATE");
            log.debug("Module name: {}", "mysql_qj_shop_order");

            JSONObject result = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_201_3007_ORDER_CREATE,
                    "F_201_3007_ORDER_CREATE",
                    "qj_shop_order",
                    param,
                    new HashSet<>());

            log.debug("插入代理订单记录结果：{}", result.toString());

            return result.getInt("errCode") == 0;
        } catch (Exception e) {
            log.error("插入代理订单记录失败", e);
            return false;
        }
    }

    /**
     * 创建或更新代理记录
     * 
     * @param openid    用户openid
     * @param agentRole 代理角色
     * @param city      城市
     * @param isRenewal 是否为续费（true=续费直接生效，false=申请需审批）
     */
    private boolean insertAgentRecord(String openid, Integer agentRole, String city, boolean isRenewal) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            // 查询用户当前的代理记录
            Integer currentRole = 0; // 默认为普通用户
            boolean hasExistingRecord = false;

            try {
                JSONObject queryParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray queryFieldKeys = new JSONArray();
                queryFieldKeys.add("openid");
                queryParam.set("__mysql_fieldkeys__", queryFieldKeys);
                queryParam.set("openid", openid);

                JSONObject queryResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                        "F_301_4003_AGENT_GET_BY_OPENID",
                        "qj_marketing_v2_agents",
                        queryParam,
                        new HashSet<>());

                log.info("查询用户现有代理记录结果：{}", queryResult.toString());

                if (queryResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> agentList = (List<Map<String, Object>>) queryResult.get("result");
                    if (agentList != null && !agentList.isEmpty()) {
                        hasExistingRecord = true;
                        // 获取最新记录的current_role（因为SQL中有ORDER BY join_date DESC LIMIT 1）
                        currentRole = Convert.toInt(agentList.get(0).get("current_role"), 0);
                        log.info("用户{}已有代理记录，当前角色：{}", openid, currentRole);
                    } else {
                        log.info("用户{}没有代理记录，将创建新记录", openid);
                    }
                } else {
                    log.warn("查询用户代理记录失败：{}", queryResult.getStr("errMsg"));
                }
            } catch (Exception e) {
                log.warn("查询用户代理记录异常：{}", e.getMessage());
            }

            if (hasExistingRecord) {
                // 存在记录，执行更新操作
                int targetStatus = isRenewal ? 1 : 2; // 续费直接生效(1)，申请需审批(2)
                String statusDesc = isRenewal ? "续费生效" : "待审批";

                log.info("更新用户{}的代理{}：apply_role={}, current_role={}, status={}({})",
                        openid, isRenewal ? "续费" : "申请", agentRole, currentRole, targetStatus, statusDesc);

                JSONObject updateParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray updateFieldKeys = new JSONArray();
                updateFieldKeys.add("apply_role");
                updateFieldKeys.add("status");
                updateFieldKeys.add("join_date");
                updateFieldKeys.add("test_times");
                updateFieldKeys.add("test_report_times");
                updateFieldKeys.add("city");

                // 续费时需要更新过期时间和current_role
                if (isRenewal) {
                    updateFieldKeys.add("expired_date");
                    updateFieldKeys.add("current_role");
                }
                updateFieldKeys.add("openid");

                updateParam.set("__mysql_fieldkeys__", updateFieldKeys);
                updateParam.set("apply_role", agentRole);
                updateParam.set("status", targetStatus);
                updateParam.set("join_date", currentTime); // 更新申请/续费时间
                updateParam.set("test_times", getTestTimesByRole(agentRole));
                updateParam.set("test_report_times", getTestReportTimesByRole(agentRole));
                updateParam.set("city", city != null ? city : ""); // 确保city总是有值

                // 续费时设置新的过期时间和角色
                if (isRenewal) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_YEAR, 365);
                    String newExpiredDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime());
                    updateParam.set("expired_date", newExpiredDate);
                    updateParam.set("current_role", agentRole); // 续费时直接设置current_role
                }
                updateParam.set("openid", openid);

                JSONObject updateResult = CRUDService.exeModifyOrDelete(
                        FunctionCodes.F_301_4005_AGENT_UPDATE,
                        "F_301_4005_AGENT_UPDATE",
                        "qj_marketing_v2_agents",
                        updateParam,
                        new HashSet<>());

                log.info("更新代理记录结果：{}", updateResult.toString());
                return updateResult.getInt("errCode") == 0;

            } else {
                // 不存在记录，执行插入操作
                int targetStatus = isRenewal ? 1 : 2; // 续费直接生效(1)，申请需审批(2)
                String statusDesc = isRenewal ? "续费生效" : "待审批";

                log.info("创建用户{}的代理{}：apply_role={}, current_role={}, status={}({})",
                        openid, isRenewal ? "续费" : "申请", agentRole, currentRole, targetStatus, statusDesc);

                // 计算过期时间（365天后）
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, 365);
                String expiredDate = sdf.format(calendar.getTime());

                // 查询推广关系
                String fromOpenid = "";
                try {
                    JSONObject promoterParam = new JSONObject(MyJsonConfig.getJsonConfig());
                    JSONArray promoterFieldKeys = new JSONArray();
                    promoterFieldKeys.add("user_openid");
                    promoterParam.set("__mysql_fieldkeys__", promoterFieldKeys);
                    promoterParam.set("user_openid", openid);

                    JSONObject promoterResult = CRUDService.exeRetrieve(
                            FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                            "F_303_4203_AGENT_USER_GET_BY_USER",
                            "qj_marketing_v2_agent_users",
                            promoterParam,
                            new HashSet<>());

                    if (promoterResult.getInt("errCode") == 0) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> agentUserList = (List<Map<String, Object>>) promoterResult
                                .get("result");
                        if (agentUserList != null && !agentUserList.isEmpty()) {
                            Object memberOpenidObj = agentUserList.get(0).get("agent_openid");
                            if (memberOpenidObj != null) {
                                fromOpenid = memberOpenidObj.toString();
                                log.info("用户{}存在推广关系，推广者openid：{}", openid, fromOpenid);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("查询推广关系异常：{}", e.getMessage());
                }

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

                // 获取用户昵称
                String nickname = getUserNickname(openid);
                if (StrUtil.isBlank(nickname)) {
                    log.warn("未找到用户昵称，openid={}", openid);
                    nickname = "未知用户";
                }

                insertParam.set("name", nickname);
                insertParam.set("apply_role", agentRole);
                insertParam.set("current_role", isRenewal ? agentRole : currentRole); // 续费时直接设置角色，申请时默认为0
                insertParam.set("status", targetStatus); // 续费直接生效(1)，申请需审批(2)
                insertParam.set("join_date", currentTime);
                insertParam.set("expired_date", expiredDate);
                insertParam.set("test_times", getTestTimesByRole(agentRole));
                insertParam.set("test_report_times", getTestReportTimesByRole(agentRole));
                insertParam.set("city", city != null ? city : "");
                insertParam.set("from_openid", fromOpenid);

                JSONObject insertResult = CRUDService.exeModifyOrDelete(
                        FunctionCodes.F_301_4004_AGENT_CREATE,
                        "F_301_4004_AGENT_CREATE",
                        "qj_marketing_v2_agents",
                        insertParam,
                        new HashSet<>());

                log.info("插入代理记录结果：{}", insertResult.toString());
                return insertResult.getInt("errCode") == 0;
            }

        } catch (Exception e) {
            log.error("处理代理记录失败", e);
            return false;
        }
    }

    /**
     * 根据代理角色获取测试次数
     */
    private int getTestTimesByRole(Integer agentRole) {
        switch (agentRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT: // 城市代理
            case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 平台代理
                return -1;
            case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 个人代理
                return -1;
            default:
                return 0;
        }
    }

    /**
     * 根据代理角色获取测试报告次数
     */
    private int getTestReportTimesByRole(Integer agentRole) {
        switch (agentRole) {
            case AgentStatusCodes.ROLE_CITY_AGENT: // 城市代理
            case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 平台代理
                return 20;
            case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 个人代理
                return 3;
            default:
                return 0;
        }
    }

    /**
     * 获取用户推广信息（包含推广人信息和下级代理信息）
     * 
     * @param userOpenid 查询的用户openid
     * @return AgentResponse 包含推广人信息和下级代理列表
     */
    public AgentResponse getUserPromotionInfo(String userOpenid) {
        if (StrUtil.isBlank(userOpenid)) {
            return AgentResponse.fail("参数错误：user_openid不能为空");
        }

        try {
            log.info("开始获取用户推广信息，user_openid={}", userOpenid);

            JSONObject responseData = new JSONObject();

            // 1. 查询用户基本信息（nick_name）
            JSONObject memberInfo = getUserMemberInfo(userOpenid);
            if (memberInfo != null) {
                responseData.set("userInfo", memberInfo);
            }

            // 2. 查询当前用户的代理级别信息
            JSONObject currentUserAgentInfo = getCurrentUserAgentInfo(userOpenid);
            if (currentUserAgentInfo != null) {
                responseData.set("currentUserAgentInfo", currentUserAgentInfo);
            }

            // 3. 查询推广人信息
            // 该读取与扫码绑定(handleQrcodeScan 的 INSERT)可能并发，读偶尔早于写落库导致查空，
            // 表现为「第一次扫码进测试页报无推广关系」。仅本入口(测试页 F_301_4014)在查空时
            // 做一次短延迟重试兜底，给并发的绑定写一点时间窗；不影响 getPromoterInfo 的其它
            // 调用方(商城/分佣/上级推广人等，那里查空是正常情况，不能加延迟)。
            JSONObject promoterInfo = getPromoterInfo(userOpenid);
            if (promoterInfo == null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                log.info("推广人信息首查为空，重试一次，user_openid={}", userOpenid);
                promoterInfo = getPromoterInfo(userOpenid);
            }
            if (promoterInfo != null) {
                responseData.set("promoterInfo", promoterInfo);
            }

            // 4. 查询下级代理列表（招募名单）
            List<JSONObject> recruitList = getRecruitList(userOpenid);
            responseData.set("recruitList", recruitList);
            responseData.set("recruitCount", recruitList.size());

            log.info("用户推广信息获取成功，user_openid={}, 推广人：{}, 下级代理数量：{}",
                    userOpenid,
                    promoterInfo != null ? promoterInfo.getStr("nick_name") : "无",
                    recruitList.size());

            return AgentResponse.success("获取用户推广信息成功", responseData);

        } catch (Exception e) {
            log.error("获取用户推广信息异常，user_openid={}", userOpenid, e);
            return AgentResponse.fail("获取用户推广信息异常：" + e.getMessage());
        }
    }

    /**
     * 获取当前用户的代理级别信息
     */
    public JSONObject getCurrentUserAgentInfo(String userOpenid) {
        try {
            JSONObject agentParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentFieldKeys = new JSONArray();
            agentFieldKeys.add("openid");

            agentParam.set("__mysql_fieldkeys__", agentFieldKeys);
            agentParam.set("openid", userOpenid);

            JSONObject agentResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    agentParam,
                    new HashSet<>());

            if (agentResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agentList = (List<Map<String, Object>>) agentResult.get("result");
                if (agentList != null && !agentList.isEmpty()) {
                    Map<String, Object> agent = agentList.get(0);
                    JSONObject agentInfo = new JSONObject();
                    Integer currentRole = Convert.toInt(agent.get("current_role"));
                    agentInfo.set("openid", agent.get("openid"));
                    agentInfo.set("current_role", currentRole);
                    agentInfo.set("role_name", AgentStatusCodes.getRoleDescription(currentRole));
                    agentInfo.set("status", agent.get("status"));
                    agentInfo.set("test_times", agent.get("test_times"));
                    agentInfo.set("test_report_times", agent.get("test_report_times"));
                    agentInfo.set("join_date", agent.get("join_date"));
                    agentInfo.set("expired_date", agent.get("expired_date"));
                    agentInfo.set("shop_id", agent.get("shop_id")); // 修正字段名为shop_id
                    return agentInfo;
                }
            }

            log.info("用户不是代理，user_openid={}", userOpenid);
            return null;

        } catch (Exception e) {
            log.error("查询当前用户代理信息失败，user_openid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取用户基本信息（从qj_member表）
     */
    private JSONObject getUserMemberInfo(String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("_openid");
            fieldKeys.add("is_del");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("_openid", userOpenid);
            param.set("is_del", 0); // 0表示未删除

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> memberList = (List<Map<String, Object>>) result.get("result");
                if (memberList != null && !memberList.isEmpty()) {
                    Map<String, Object> member = memberList.get(0);
                    JSONObject memberInfo = new JSONObject();
                    memberInfo.set("openid", member.get("_openid"));
                    memberInfo.set("nick_name", member.get("nick_name"));
                    memberInfo.set("avatar_url", member.get("avatar_url"));
                    return memberInfo;
                }
            }

            log.warn("未找到用户信息，user_openid={}", userOpenid);
            return null;

        } catch (Exception e) {
            log.error("查询用户基本信息失败，user_openid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取推广人信息
     */
    public JSONObject getPromoterInfo(String userOpenid) {
        try {
            // 1. 从qj_marketing_v2_agent_users表查询推广关系
            JSONObject agentUserParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentUserFieldKeys = new JSONArray();
            agentUserFieldKeys.add("user_openid");

            agentUserParam.set("__mysql_fieldkeys__", agentUserFieldKeys);
            agentUserParam.set("user_openid", userOpenid);

            JSONObject agentUserResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    agentUserParam,
                    new HashSet<>());

            if (agentUserResult.getInt("errCode") != 0 || agentUserResult.get("result") == null) {
                log.info("用户无推广关系，user_openid={}", userOpenid);
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentUserList = (List<Map<String, Object>>) agentUserResult.get("result");
            if (agentUserList == null || agentUserList.isEmpty()) {
                return null;
            }

            String memberOpenid = (String) agentUserList.get(0).get("agent_openid");
            if (StrUtil.isBlank(memberOpenid)) {
                return null;
            }

            // 2. 根据agent_openid查询推广人的基本信息和代理级别
            JSONObject promoterInfo = new JSONObject();

            // 查询推广人基本信息
            JSONObject promoterMemberInfo = getUserMemberInfo(memberOpenid);
            if (promoterMemberInfo != null) {
                promoterInfo.putAll(promoterMemberInfo);
            }

            // 查询推广人代理级别
            JSONObject agentParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentFieldKeys = new JSONArray();
            agentFieldKeys.add("openid");

            agentParam.set("__mysql_fieldkeys__", agentFieldKeys);
            agentParam.set("openid", memberOpenid);

            JSONObject agentResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    agentParam,
                    new HashSet<>());

            if (agentResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agentList = (List<Map<String, Object>>) agentResult.get("result");
                if (agentList != null && !agentList.isEmpty()) {
                    Map<String, Object> agent = agentList.get(0);
                    Integer currentRole = Convert.toInt(agent.get("current_role"));
                    promoterInfo.set("current_role", currentRole);
                    promoterInfo.set("role_name", AgentStatusCodes.getRoleDescription(currentRole));
                    promoterInfo.set("status", agent.get("status"));
                    promoterInfo.set("join_date", agent.get("join_date"));
                    promoterInfo.set("shop_id", agent.get("shop_id")); // 修正字段名为shop_id
                    promoterInfo.set("agent_openid", memberOpenid); // 添加推广人openid（保持与调用方一致）
                }
            }

            return promoterInfo;

        } catch (Exception e) {
            log.error("查询推广人信息失败，user_openid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取下级代理列表（招募名单）
     */
    private List<JSONObject> getRecruitList(String userOpenid) {
        List<JSONObject> recruitList = new ArrayList<>();

        try {
            // 1. 从qj_marketing_v2_agent_users表查询所有被该用户推广的人
            JSONObject agentUserParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentUserFieldKeys = new JSONArray();
            agentUserFieldKeys.add("agent_openid");
            agentUserParam.set("__mysql_fieldkeys__", agentUserFieldKeys);
            agentUserParam.set("agent_openid", userOpenid);

            JSONObject agentUserResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT,
                    "F_303_4204_AGENT_USER_GET_BY_AGENT",
                    "qj_marketing_v2_agent_users",
                    agentUserParam,
                    new HashSet<>());

            if (agentUserResult.getInt("errCode") != 0 || agentUserResult.get("result") == null) {
                log.info("用户无下级推广关系，user_openid={}", userOpenid);
                return recruitList;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentUserList = (List<Map<String, Object>>) agentUserResult.get("result");

            // 2. 遍历每个下级，获取其基本信息和代理级别
            for (Map<String, Object> agentUser : agentUserList) {
                String subordinateOpenid = (String) agentUser.get("user_openid");
                if (StrUtil.isBlank(subordinateOpenid)) {
                    continue;
                }

                JSONObject recruitInfo = new JSONObject();

                // 获取下级基本信息
                JSONObject memberInfo = getUserMemberInfo(subordinateOpenid);
                if (memberInfo != null) {
                    recruitInfo.putAll(memberInfo);
                }

                // 获取下级代理级别
                JSONObject agentParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray agentFieldKeys = new JSONArray();
                agentFieldKeys.add("openid");

                agentParam.set("__mysql_fieldkeys__", agentFieldKeys);
                agentParam.set("openid", subordinateOpenid);

                JSONObject agentResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                        "F_301_4003_AGENT_GET_BY_OPENID",
                        "qj_marketing_v2_agents",
                        agentParam,
                        new HashSet<>());

                if (agentResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> agentList = (List<Map<String, Object>>) agentResult.get("result");
                    if (agentList != null && !agentList.isEmpty()) {
                        Map<String, Object> agent = agentList.get(0);
                        Integer currentRole = Convert.toInt(agent.get("current_role"));
                        recruitInfo.set("current_role", currentRole);
                        recruitInfo.set("role_name", AgentStatusCodes.getRoleDescription(currentRole));
                        recruitInfo.set("status", agent.get("status"));
                        recruitInfo.set("join_date", agent.get("join_date"));
                    } else {
                        // 如果不是代理，设置为普通用户
                        recruitInfo.set("current_role", 0);
                        recruitInfo.set("role_name", "普通用户");
                        recruitInfo.set("status", 0);
                    }
                }

                // 添加推广时间与授权信息
                recruitInfo.set("recruit_date", agentUser.get("join_date"));
                Object reportQualifiedObj = agentUser.get("report_qualified");
                recruitInfo.set("report_qualified",
                        reportQualifiedObj != null ? Convert.toInt(reportQualifiedObj, 0) : 0);
                recruitInfo.set("notes", agentUser.get("notes")); // 返回备注字段
                recruitInfo.set("user_openid", subordinateOpenid);

                recruitList.add(recruitInfo);
            }

        } catch (Exception e) {
            log.error("查询下级代理列表失败，user_openid={}", userOpenid, e);
        }

        return recruitList;
    }

    /**
     * 判断用户是否可以申请指定代理
     */
    public boolean canApplyForAgent(String openid, int applyRole) {
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
            if (result.getInt("errCode") != 0) {
                log.warn("canApplyForAgent: 查询失败，openid={}, applyRole={}, 默认允许", openid, applyRole);
                return true; // 查询失败，默认允许
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) result.get("result");
            int currentRole = 0;
            if (agentList != null && !agentList.isEmpty()) {
                Object cr = agentList.get(0).get("current_role");
                if (cr != null) {
                    try {
                        currentRole = Integer.parseInt(cr.toString());
                    } catch (Exception e) {
                        log.warn("canApplyForAgent: 解析current_role异常，openid={}, applyRole={}, cr={}", openid, applyRole,
                                cr);
                    }
                }
            }
            boolean canApply;
            if (currentRole == 0) {
                canApply = true; // 普通用户可申请任何代理
                log.info("canApplyForAgent: 普通用户可申请任何代理，openid={}, applyRole={}, currentRole={}, canApply={}", openid,
                        applyRole, currentRole, canApply);
            } else if (currentRole > applyRole) {
                canApply = true;
                log.info("canApplyForAgent: 代理可升级到更高等级，openid={}, applyRole={}, currentRole={}, canApply={}", openid,
                        applyRole, currentRole, canApply);
            } else {
                canApply = false;
                log.info("canApplyForAgent: 不能降级或平级，openid={}, applyRole={}, currentRole={}, canApply={}", openid,
                        applyRole, currentRole, canApply);
            }
            log.info("canApplyForAgent: openid={}, applyRole={}, currentRole={}, canApply={}", openid, applyRole,
                    currentRole, canApply);
            return canApply;
        } catch (Exception e) {
            log.error("canApplyForAgent: 异常，openid={}, applyRole={}, 默认允许，异常信息：{}", openid, applyRole, e.getMessage());
            return true; // 异常时默认允许
        }
    }

    /**
     * 校验用户是否有免费形象测试资格
     *
     * @param openid       当前用户openid（代理/个人）
     * @param targetOpenid 被测试人openid
     * @return AgentResponse
     */
    public AgentResponse checkTestQualification(String openid, String targetOpenid) {
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(targetOpenid)) {
            return AgentResponse.fail("参数错误：openid或targetOpenid不能为空");
        }
        try {
            // 1. 查询qj_marketing_v2_agents表，获取当前用户身份
            JSONObject agentParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentFieldKeys = new JSONArray();
            agentFieldKeys.add("openid");
            agentParam.set("__mysql_fieldkeys__", agentFieldKeys);
            agentParam.set("openid", openid);
            JSONObject agentResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    agentParam,
                    new HashSet<>());
            int currentRole = 0;
            if (agentResult.getInt("errCode") == 0) {
                List<Map<String, Object>> agentList = (List<Map<String, Object>>) agentResult.get("result");
                if (agentList != null && !agentList.isEmpty()) {
                    Object cr = agentList.get(0).get("current_role");
                    if (cr != null) {
                        try {
                            currentRole = Integer.parseInt(cr.toString());
                        } catch (Exception e) {
                            log.warn("checkTestQualification: 解析current_role异常，openid={}, cr={}", openid, cr);
                        }
                    }
                }
            }
            // 个人代理逻辑
            if (currentRole == AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                if (openid.equals(targetOpenid)) {
                    // 个人代理自己测自己，始终有资格
                    return AgentResponse.success("个人代理自己测自己，资格通过", 1);
                } else {
                    // 统计推广人数
                    JSONObject countParam = new JSONObject(MyJsonConfig.getJsonConfig());
                    JSONArray countFieldKeys = new JSONArray();
                    countFieldKeys.add("agent_openid");
                    countParam.set("__mysql_fieldkeys__", countFieldKeys);
                    countParam.set("agent_openid", openid);
                    JSONObject countResult = CRUDService.exeRetrieve(
                            FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT,
                            "F_303_4204_AGENT_USER_GET_BY_AGENT",
                            "qj_marketing_v2_agent_users",
                            countParam,
                            new HashSet<>());
                    int promoteCount = 0;
                    if (countResult.getInt("errCode") == 0) {
                        List<Map<String, Object>> promoteList = (List<Map<String, Object>>) countResult.get("result");
                        if (promoteList != null) {
                            promoteCount = promoteList.size();
                        }
                    }
                    if (promoteCount < 100) {
                        return AgentResponse.success("个人代理推广关系未超限，资格通过", 1);
                    } else {
                        return AgentResponse.success("个人代理推广关系已达上限", 0);
                    }
                }
            }
            // 其它代理类型或普通用户，按原有逻辑
            // 1. 代理状态正常
            if (currentRole != 0) {
                return AgentResponse.success("资格校验通过：代理状态正常", 1);
            }
            // 2. 查询qj_marketing_v2_agent_users表，user_openid==targetOpenid
            JSONObject userParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray userFieldKeys = new JSONArray();
            userFieldKeys.add("user_openid");
            userParam.set("__mysql_fieldkeys__", userFieldKeys);
            userParam.set("user_openid", targetOpenid);
            JSONObject userResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    userParam,
                    new HashSet<>());
            if (userResult.getInt("errCode") == 0) {
                List<Map<String, Object>> userList = (List<Map<String, Object>>) userResult.get("result");
                if (userList != null && !userList.isEmpty()) {
                    return AgentResponse.success("资格校验通过：有推广关系", 1);
                }
            }
            // 3. 不通过
            return AgentResponse.success("无免费测试资格", 0);
        } catch (Exception e) {
            log.error("校验形象测试资格异常", e);
            return AgentResponse.fail("校验形象测试资格异常：" + e.getMessage());
        }
    }

    /**
     * 兼容老接口：只传openid时，targetOpenid=openid
     */
    public AgentResponse checkTestQualification(String openid) {
        return checkTestQualification(openid, openid);
    }

    /**
     * 检查用户报告资格
     * 代理本人自动拥有资格，普通用户需由上级代理授权
     */
    public AgentResponse checkReportQualification(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            // 1. 代理本人直接拥有资格
            JSONObject agentInfo = getCurrentUserAgentInfo(openid);
            if (agentInfo != null) {
                Integer currentRole = Convert.toInt(agentInfo.get("current_role"), 0);
                Integer status = Convert.toInt(agentInfo.get("status"), 0);
                if (currentRole != null && currentRole > 0 && status != null
                        && status == AgentStatusCodes.AGENT_STATUS_VALID) {
                    return AgentResponse.success("代理用户具备报告资格", 1);
                }
            }

            // 2. 查询推广关系是否授权报告资格
            JSONObject agentUserParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            fieldKeys.add("report_qualified");

            agentUserParam.set("__mysql_fieldkeys__", fieldKeys);
            agentUserParam.set("user_openid", openid);

            JSONObject agentUserResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    agentUserParam,
                    new HashSet<>());

            if (agentUserResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relationList = (List<Map<String, Object>>) agentUserResult.get("result");
                if (relationList != null && !relationList.isEmpty()) {
                    for (Map<String, Object> relation : relationList) {
                        Object qualified = relation.get("report_qualified");
                        if (qualified != null && Convert.toInt(qualified, 0) == 1) {
                            return AgentResponse.success("普通用户已获得报告资格", 1);
                        }
                    }
                }
            }

            return AgentResponse.success("普通用户未获得报告资格", 0);
        } catch (Exception e) {
            log.error("检查报告资格异常，openid={}", openid, e);
            return AgentResponse.fail("检查报告资格异常：" + e.getMessage());
        }
    }

    /**
     * 扣减个人代理的测试次数
     */
    public AgentResponse consumeTestQuota(String agentOpenid, String userOpenid) {
        if (StrUtil.isBlank(agentOpenid) || StrUtil.isBlank(userOpenid)) {
            return AgentResponse.fail("参数错误：agentOpenid、userOpenid不能为空");
        }

        try {
            JSONObject agentInfo = getCurrentUserAgentInfo(agentOpenid);
            if (agentInfo == null) {
                return AgentResponse.fail("未找到代理信息");
            }

            Integer status = Convert.toInt(agentInfo.get("status"), 0);
            if (status == null || status != AgentStatusCodes.AGENT_STATUS_VALID) {
                return AgentResponse.fail("代理状态异常，无法扣减测试次数");
            }

            Integer role = Convert.toInt(agentInfo.get("current_role"), 0);
            JSONObject data = new JSONObject();
            data.set("agentRole", role);
            if (agentOpenid.equals(userOpenid)) {
                data.set("consume", false);
                data.set("remainingTimes", Convert.toInt(agentInfo.get("test_times"), -1));
                return AgentResponse.success("代理本人测试无需扣减", data);
            }

            if (role == AgentStatusCodes.ROLE_CITY_AGENT || role == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                data.set("consume", false);
                data.set("remainingTimes", -1);
                return AgentResponse.success("城市/平台代理测试次数无限", data);
            }

            if (role != AgentStatusCodes.ROLE_PERSONAL_AGENT) {
                return AgentResponse.fail("当前推广人不是有效代理，无法扣减测试次数");
            }

            Integer testTimes = Convert.toInt(agentInfo.get("test_times"), 0);
            if (testTimes == null || testTimes <= 0) {
                return AgentResponse.fail("代理可用测试次数不足");
            }

            String updateSql = "UPDATE qj_marketing_v2_agents SET test_times = test_times - 1 "
                    + "WHERE openid = ? AND is_del = 0 AND test_times > 0";
            int update = com.qj.db.DBConfig.getDb(null, "qj").execute(updateSql, agentOpenid);
            if (update <= 0) {
                return AgentResponse.fail("扣减测试次数失败，请稍后重试");
            }

            JSONObject refreshedInfo = getCurrentUserAgentInfo(agentOpenid);
            int remaining = refreshedInfo != null ? Convert.toInt(refreshedInfo.get("test_times"), 0) : testTimes - 1;
            data.set("consume", true);
            data.set("remainingTimes", remaining);
            return AgentResponse.success("扣减测试次数成功", data);
        } catch (Exception e) {
            log.error("扣减测试次数异常，agentOpenid={}, userOpenid={}", agentOpenid, userOpenid, e);
            return AgentResponse.fail("扣减测试次数异常：" + e.getMessage());
        }
    }

    /**
     * 扣减代理的报告次数（普通用户生成报告时调用）
     * 
     * @param userOpenid 普通用户openid
     * @return AgentResponse 包含扣减结果
     */
    public AgentResponse consumeReportQuota(String userOpenid) {
        if (StrUtil.isBlank(userOpenid)) {
            return AgentResponse.fail("参数错误：userOpenid不能为空");
        }

        try {
            // 1. 查询用户的推广关系，获取上级代理openid
            JSONObject promoterInfo = getPromoterInfo(userOpenid);
            if (promoterInfo == null) {
                log.info("用户{}无推广关系，无需扣减报告次数", userOpenid);
                JSONObject data = new JSONObject();
                data.set("consume", false);
                data.set("reason", "无推广关系");
                return AgentResponse.success("无推广关系，无需扣减", data);
            }

            String agentOpenid = promoterInfo.getStr("agent_openid");
            if (StrUtil.isBlank(agentOpenid)) {
                log.info("用户{}的推广人信息不完整", userOpenid);
                JSONObject data = new JSONObject();
                data.set("consume", false);
                data.set("reason", "推广人信息不完整");
                return AgentResponse.success("推广人信息不完整", data);
            }

            // 2. 获取代理信息
            JSONObject agentInfo = getCurrentUserAgentInfo(agentOpenid);
            if (agentInfo == null) {
                log.info("推广人{}不是代理", agentOpenid);
                JSONObject data = new JSONObject();
                data.set("consume", false);
                data.set("reason", "推广人不是代理");
                return AgentResponse.success("推广人不是代理", data);
            }

            Integer status = Convert.toInt(agentInfo.get("status"), 0);
            if (status == null || status != AgentStatusCodes.AGENT_STATUS_VALID) {
                return AgentResponse.fail("代理状态异常，无法扣减报告次数");
            }

            Integer role = Convert.toInt(agentInfo.get("current_role"), 0);
            Integer reportTimes = Convert.toInt(agentInfo.get("test_report_times"), 0);

            JSONObject data = new JSONObject();
            data.set("agentRole", role);
            data.set("agentOpenid", agentOpenid);

            // 3. 城市代理和平台代理报告次数无限（-1表示无限）
            if (role == AgentStatusCodes.ROLE_CITY_AGENT || role == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                if (reportTimes != null && reportTimes == -1) {
                    data.set("consume", false);
                    data.set("remainingTimes", -1);
                    return AgentResponse.success("城市/平台代理报告次数无限", data);
                }
            }

            // 4. 检查报告次数是否充足
            if (reportTimes == null || reportTimes <= 0) {
                return AgentResponse.fail("代理可用报告次数不足");
            }

            // 5. 执行扣减
            String updateSql = "UPDATE qj_marketing_v2_agents SET test_report_times = test_report_times - 1 "
                    + "WHERE openid = ? AND is_del = 0 AND test_report_times > 0";
            int update = com.qj.db.DBConfig.getDb(null, "qj").execute(updateSql, agentOpenid);
            if (update <= 0) {
                return AgentResponse.fail("扣减报告次数失败，请稍后重试");
            }

            // 6. 返回扣减后的剩余次数
            JSONObject refreshedInfo = getCurrentUserAgentInfo(agentOpenid);
            int remaining = refreshedInfo != null ? Convert.toInt(refreshedInfo.get("test_report_times"), 0)
                    : reportTimes - 1;
            data.set("consume", true);
            data.set("remainingTimes", remaining);
            log.info("扣减报告次数成功，agentOpenid={}, userOpenid={}, 剩余次数={}", agentOpenid, userOpenid, remaining);
            return AgentResponse.success("扣减报告次数成功", data);
        } catch (Exception e) {
            log.error("扣减报告次数异常，userOpenid={}", userOpenid, e);
            return AgentResponse.fail("扣减报告次数异常：" + e.getMessage());
        }
    }

    /**
     * 授权或撤销普通用户报告资格
     */
    public AgentResponse grantReportQualification(String agentOpenid, String userOpenid, boolean grant, String notes) {
        if (StrUtil.isBlank(agentOpenid) || StrUtil.isBlank(userOpenid)) {
            return AgentResponse.fail("参数错误：agentOpenid、userOpenid不能为空");
        }

        try {
            String updateSql = "UPDATE qj_marketing_v2_agent_users SET report_qualified = ?, notes = ? "
                    + "WHERE agent_openid = ? AND user_openid = ?";
            int updated = com.qj.db.DBConfig.getDb(null, "qj")
                    .execute(updateSql, grant ? 1 : 0, notes, agentOpenid, userOpenid);

            if (updated <= 0) {
                return AgentResponse.fail("未找到对应的推广关系或更新失败");
            }

            JSONObject data = new JSONObject();
            data.set("reportQualified", grant);
            data.set("notes", notes);
            return AgentResponse.success(grant ? "已赋予报告资格" : "已撤销报告资格", data);
        } catch (Exception e) {
            log.error("更新报告资格异常，agentOpenid={}, userOpenid={}, grant={}, notes={}",
                    agentOpenid, userOpenid, grant, notes, e);
            return AgentResponse.fail("更新报告资格异常：" + e.getMessage());
        }
    }

    /**
     * 检查用户资格并自动更新buy_report字段
     * 如果用户有资格，自动设置buy_report=1，这样用户就可以直接使用qjapi的PDF功能
     * 
     * @param openid 用户openid
     * @return AgentResponse 包含资格检查结果和更新状态
     */
    public AgentResponse checkQualificationAndUpdateBuyReport(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            // 1. 先检查用户报告资格
            AgentResponse qualificationResp = checkReportQualification(openid);
            if (!qualificationResp.isSuccess()) {
                return qualificationResp;
            }

            // 2. 解析资格结果
            Object data = qualificationResp.getData();
            int qualified = 0;
            if (data instanceof Integer) {
                qualified = (Integer) data;
            } else if (data instanceof String) {
                try {
                    qualified = Integer.parseInt((String) data);
                } catch (Exception ignore) {
                }
            }

            // 3. 如果有资格，更新buy_report字段
            if (qualified == 1) {
                try {
                    // 更新qj_member表中的buy_report字段
                    String updateSql = "UPDATE qj_member SET buy_report = 1 WHERE _openid = ? AND is_del = 0";
                    int updateResult = com.qj.db.DBConfig.getDb(null, "qj").execute(updateSql, openid);

                    if (updateResult > 0) {
                        log.info("用户资格检查通过，已自动更新buy_report=1，openid: {}", openid);
                        return AgentResponse.success("资格检查通过，已自动获得报告权限", 1);
                    } else {
                        log.warn("用户资格检查通过，但更新buy_report失败，openid: {}", openid);
                        return AgentResponse.success("资格检查通过，但更新权限失败", 1);
                    }
                } catch (Exception e) {
                    log.error("更新buy_report字段失败，openid: {}", openid, e);
                    return AgentResponse.success("资格检查通过，但更新权限异常", 1);
                }
            } else {
                // 没有资格，返回0
                return AgentResponse.success("无免费报告资格", 0);
            }

        } catch (Exception e) {
            log.error("检查资格并更新buy_report异常，openid: {}", openid, e);
            return AgentResponse.fail("检查资格异常：" + e.getMessage());
        }
    }

    /**
     * Controller参数转发用：canApplyForAgent
     */
    public JSONObject canApplyForAgentService(String sBody) {
        JSONObject result = new JSONObject();
        try {
            JSONObject params = cn.hutool.json.JSONUtil.parseObj(sBody);
            String openid = params.getStr("openid");
            Integer applyRole = params.getInt("applyRole");
            boolean canApply = this.canApplyForAgent(openid, applyRole != null ? applyRole : 0);
            result.set("canApply", canApply);
        } catch (Exception e) {
            result.set("canApply", true);
            result.set("errMsg", e.getMessage());
        }
        return result;
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
     * 获取代理信息（含查询权限字段）- 小程序端使用
     * 
     * @param openid 用户openid
     * @return AgentResponse 包含代理信息（含query_auth字段）
     */
    public AgentResponse getAgentInfo(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.info("开始获取代理信息，openid={}", openid);

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", openid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4051_AGENT_GET_INFO,
                    "F_301_4051_AGENT_GET_INFO",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            log.debug("查询代理信息结果：{}", result.toString());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("查询代理信息失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) result.get("result");

            if (agentList == null || agentList.isEmpty()) {
                // 非代理用户
                JSONObject agentInfo = new JSONObject();
                agentInfo.set("isAgent", false);
                agentInfo.set("queryAuth", 0);
                return AgentResponse.success("用户非代理", agentInfo);
            }

            Map<String, Object> agentData = agentList.get(0);
            JSONObject agentInfo = new JSONObject();
            agentInfo.set("isAgent", true);
            agentInfo.set("openid", agentData.get("openid"));
            agentInfo.set("name", agentData.get("name"));
            agentInfo.set("status", Convert.toInt(agentData.get("status"), 0));
            agentInfo.set("currentRole", Convert.toInt(agentData.get("current_role"), 0));
            agentInfo.set("applyRole", Convert.toInt(agentData.get("apply_role"), 0));
            agentInfo.set("testTimes", Convert.toInt(agentData.get("test_times"), 0));
            agentInfo.set("testReportTimes", Convert.toInt(agentData.get("test_report_times"), 0));
            agentInfo.set("queryAuth", Convert.toInt(agentData.get("query_auth"), 0));
            agentInfo.set("expiredDate", agentData.get("expired_date"));

            log.info("获取代理信息成功，openid={}, queryAuth={}", openid, agentInfo.getInt("queryAuth"));
            return AgentResponse.success("获取代理信息成功", agentInfo);

        } catch (Exception e) {
            log.error("获取代理信息异常，openid={}", openid, e);
            return AgentResponse.fail("获取代理信息异常：" + e.getMessage());
        }
    }

}