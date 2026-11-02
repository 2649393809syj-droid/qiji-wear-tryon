package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.consts.AgentStatusCodes;
import com.qj.entities.AgentResponse;
import com.qj.services.AdminAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员临时代理控制器
 * 用于小程序端管理员添加临时代理功能
 * 
 * @author system
 */
@RestController
@RequestMapping("/admin/temporary-agent")
public class AdminTemporaryAgentController {

    private static final Logger log = LoggerFactory.getLogger(AdminTemporaryAgentController.class);

    @Autowired
    private AdminAgentService adminAgentService;

    /**
     * 添加临时代理
     * 
     * @param requestBody 请求体，包含openid、agentRole、validDays
     * @return AgentResponse
     */
    @PostMapping("/add")
    public AgentResponse addTemporaryAgent(@RequestBody String requestBody) {
        log.info("addTemporaryAgent: 接收到添加临时代理请求, requestBody={}", requestBody);
        
        try {
            // 解析请求参数
            JSONObject requestJson = JSONUtil.parseObj(requestBody);
            String openid = requestJson.getStr("openid");
            Integer agentRole = requestJson.getInt("agentRole");
            Integer validDays = requestJson.getInt("validDays");
            String city = requestJson.getStr("city");

            log.info("addTemporaryAgent: 解析参数, openid={}, agentRole={}, validDays={}", 
                    openid, agentRole, validDays);
            
            // 参数校验
            if (StrUtil.isBlank(openid)) {
                log.warn("addTemporaryAgent: openid为空");
                return AgentResponse.fail("参数错误：openid不能为空");
            }
            
            if (agentRole == null) {
                log.warn("addTemporaryAgent: agentRole为空");
                return AgentResponse.fail("参数错误：代理等级不能为空");
            }
            
            if (validDays == null || validDays <= 0) {
                log.warn("addTemporaryAgent: validDays无效, validDays={}", validDays);
                return AgentResponse.fail("参数错误：有效天数必须大于0");
            }
            
            // 调用服务层方法
            return adminAgentService.addTemporaryAgent(openid, agentRole, validDays,city);
            
        } catch (Exception e) {
            log.error("addTemporaryAgent: 添加临时代理异常", e);
            return AgentResponse.fail("添加临时代理失败：" + e.getMessage());
        }
    }

    /**
     * 审批代理申请（复用原有方法）
     * 
     * @param requestBody 请求体，包含openid、currentPrice
     * @return AgentResponse
     */
    @PostMapping("/approve")
    public AgentResponse approveAgent(@RequestBody String requestBody) {
        log.info("approveAgent: 接收到审批代理请求, requestBody={}", requestBody);
        
        try {
            // 解析请求参数
            JSONObject requestJson = JSONUtil.parseObj(requestBody);
            String openid = requestJson.getStr("openid");
            Integer currentPrice = requestJson.getInt("currentPrice");
            
            log.info("approveAgent: 解析参数, openid={}, currentPrice={}", openid, currentPrice);
            
            // 参数校验
            if (StrUtil.isBlank(openid)) {
                log.warn("approveAgent: openid为空");
                return AgentResponse.fail("参数错误：openid不能为空");
            }
            
            if (currentPrice == null) {
                currentPrice = 0; // 默认价格为0
            }
            
            // 调用原有的审批方法
            return adminAgentService.approveAgent(openid, currentPrice);
            
        } catch (Exception e) {
            log.error("approveAgent: 审批代理申请异常", e);
            return AgentResponse.fail("审批代理申请失败：" + e.getMessage());
        }
    }


    /**
     * 统一代理查询接口（小程序端）
     * 直接调用AdminAgentService现有方法
     *
     * @param requestBody 请求体，包含queryType和相关参数
     * @return AgentResponse
     */
    @PostMapping("/agentQuery")
    public AgentResponse agentQuery(@RequestBody String requestBody) {
        log.info("agentQuery: 接收到代理查询请求, requestBody={}", requestBody);

        try {
            JSONObject requestJson = JSONUtil.parseObj(requestBody);
            String queryType = requestJson.getStr("queryType", "list");

            log.info("agentQuery: 查询类型={}", queryType);

            // 根据查询类型直接调用现有服务方法
            switch (queryType.toLowerCase()) {
                case "list":
                    // 代理列表查询
                    Integer page = requestJson.getInt("page", 1);
                    Integer pageSize = requestJson.getInt("pageSize", 10);
                    Integer status = requestJson.getInt("status");
                    Integer role = requestJson.getInt("role");
                    return adminAgentService.getAgentList(status, role, page, pageSize);

                case "pending":
                    // 待审批代理查询
                    Integer pendingPage = requestJson.getInt("page", 1);
                    Integer pendingPageSize = requestJson.getInt("pageSize", 10);
                    return adminAgentService.getAgentList(AgentStatusCodes.AGENT_STATUS_PENDING, null, pendingPage, pendingPageSize);

                default:
                    return AgentResponse.fail("不支持的查询类型：" + queryType);
            }

        } catch (Exception e) {
            log.error("agentQuery: 代理查询异常", e);
            return AgentResponse.fail("代理查询失败：" + e.getMessage());
        }
    }

}
