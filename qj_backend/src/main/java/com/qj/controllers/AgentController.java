package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代理相关接口
 */
@RestController
@RequestMapping("/qj/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    @Autowired
    private AgentService agentService;

    /**
     * 扣减个人代理的测试次数
     */
    @PostMapping("/consumeTestQuota")
    public AgentResponse consumeTestQuota(@RequestBody String requestBody) {
        log.info("扣减测试次数请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String agentOpenid = reqJson.getStr("agentOpenid");
            String userOpenid = reqJson.getStr("userOpenid");

            if (StrUtil.isBlank(agentOpenid) || StrUtil.isBlank(userOpenid)) {
                return AgentResponse.fail("参数错误：agentOpenid、userOpenid不能为空");
            }

            return agentService.consumeTestQuota(agentOpenid, userOpenid);
        } catch (Exception e) {
            log.error("扣减测试次数异常", e);
            return AgentResponse.fail("扣减测试次数异常：" + e.getMessage());
        }
    }

    /**
     * 授权或撤销普通用户的报告资格，并更新备注
     */
    @PostMapping("/grantReportQualification")
    public AgentResponse grantReportQualification(@RequestBody String requestBody) {
        log.info("更新报告资格请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String agentOpenid = reqJson.getStr("agentOpenid");
            String userOpenid = reqJson.getStr("userOpenid");
            Boolean grant = reqJson.getBool("grant", Boolean.TRUE);
            String notes = reqJson.getStr("notes", "");

            if (StrUtil.isBlank(agentOpenid) || StrUtil.isBlank(userOpenid)) {
                return AgentResponse.fail("参数错误：agentOpenid、userOpenid不能为空");
            }

            return agentService.grantReportQualification(agentOpenid, userOpenid, grant != null && grant, notes);
        } catch (Exception e) {
            log.error("更新报告资格异常", e);
            return AgentResponse.fail("更新报告资格异常：" + e.getMessage());
        }
    }

    /**
     * 扣减代理的报告次数（普通用户生成报告时调用）
     */
    @PostMapping("/consumeReportQuota")
    public AgentResponse consumeReportQuota(@RequestBody String requestBody) {
        log.info("扣减报告次数请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String userOpenid = reqJson.getStr("userOpenid");

            if (StrUtil.isBlank(userOpenid)) {
                return AgentResponse.fail("参数错误：userOpenid不能为空");
            }

            return agentService.consumeReportQuota(userOpenid);
        } catch (Exception e) {
            log.error("扣减报告次数异常", e);
            return AgentResponse.fail("扣减报告次数异常：" + e.getMessage());
        }
    }

    /**
     * 获取代理信息（含查询权限字段）- 小程序端使用
     */
    @PostMapping("/getAgentInfo")
    public AgentResponse getAgentInfo(@RequestBody String requestBody) {
        log.info("获取代理信息请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return agentService.getAgentInfo(openid);
        } catch (Exception e) {
            log.error("获取代理信息异常", e);
            return AgentResponse.fail("获取代理信息异常：" + e.getMessage());
        }
    }
}
