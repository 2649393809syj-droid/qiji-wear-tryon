package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.SchemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 方案相关接口
 */
@RestController
@RequestMapping("/qj/scheme")
public class SchemeController {

    private static final Logger log = LoggerFactory.getLogger(SchemeController.class);

    @Autowired
    private SchemeService schemeService;

    /**
     * 添加方案（普通保存）
     */
    @PostMapping("/add")
    public AgentResponse addScheme(@RequestBody String requestBody) {
        log.info("添加方案请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");
            String collocationData = reqJson.getStr("collocationData");
            String humanData = reqJson.getStr("humanData");
            Long schemeTime = reqJson.getLong("schemeTime");
            Integer fromShopAdviser = reqJson.getInt("fromShopAdviser");
            String push = reqJson.getStr("push");
            Integer shopId = reqJson.getInt("shopId");
            String customerOpenid = reqJson.getStr("customerOpenid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }
            if (StrUtil.isBlank(collocationData) || StrUtil.isBlank(humanData)) {
                return AgentResponse.fail("参数错误：collocationData和humanData不能为空");
            }

            return schemeService.addScheme(openid, collocationData, humanData,
                    schemeTime, fromShopAdviser, push, shopId, customerOpenid);
        } catch (Exception e) {
            log.error("添加方案异常", e);
            return AgentResponse.fail("添加方案异常：" + e.getMessage());
        }
    }

    /**
     * 推送方案给指定用户（双向保存+通知）
     */
    @PostMapping("/pushToUser")
    public AgentResponse pushSchemeToUser(@RequestBody String requestBody) {
        log.info("推送方案请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String operatorOpenid = reqJson.getStr("operatorOpenid");
            String targetOpenid = reqJson.getStr("targetOpenid");
            String collocationData = reqJson.getStr("collocationData");
            String humanData = reqJson.getStr("humanData");
            Long schemeTime = reqJson.getLong("schemeTime");
            Integer fromShopAdviser = reqJson.getInt("fromShopAdviser");
            Integer shopId = reqJson.getInt("shopId");

            if (StrUtil.isBlank(operatorOpenid) || StrUtil.isBlank(targetOpenid)) {
                return AgentResponse.fail("参数错误：operatorOpenid和targetOpenid不能为空");
            }
            if (StrUtil.isBlank(collocationData) || StrUtil.isBlank(humanData)) {
                return AgentResponse.fail("参数错误：collocationData和humanData不能为空");
            }

            return schemeService.pushSchemeToUser(operatorOpenid, targetOpenid,
                    collocationData, humanData, schemeTime, fromShopAdviser, shopId);
        } catch (Exception e) {
            log.error("推送方案异常", e);
            return AgentResponse.fail("推送方案异常：" + e.getMessage());
        }
    }

    /**
     * 获取方案列表（支持sourceType筛选）
     */
    @PostMapping("/list")
    public AgentResponse getSchemeList(@RequestBody String requestBody) {
        log.info("获取方案列表请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");
            Integer sourceType = reqJson.getInt("sourceType");
            Integer pageNumber = reqJson.getInt("pageNumber");
            Integer pageSize = reqJson.getInt("pageSize");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return schemeService.getSchemeList(openid, sourceType, pageNumber, pageSize);
        } catch (Exception e) {
            log.error("获取方案列表异常", e);
            return AgentResponse.fail("获取方案列表异常：" + e.getMessage());
        }
    }

    /**
     * 检查未读推送通知
     */
    @PostMapping("/checkNotification")
    public AgentResponse checkNotification(@RequestBody String requestBody) {
        log.info("检查通知请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return schemeService.checkNotification(openid);
        } catch (Exception e) {
            log.error("检查通知异常", e);
            return AgentResponse.fail("检查通知异常：" + e.getMessage());
        }
    }

    /**
     * 标记通知已读
     */
    @PostMapping("/markRead")
    public AgentResponse markNotificationRead(@RequestBody String requestBody) {
        log.info("标记已读请求：{}", requestBody);
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return schemeService.markNotificationRead(openid);
        } catch (Exception e) {
            log.error("标记已读异常", e);
            return AgentResponse.fail("标记已读异常：" + e.getMessage());
        }
    }
}
