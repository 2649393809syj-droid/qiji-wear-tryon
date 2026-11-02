package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.EvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 评测控制器
 * 提供用户评测相关的REST API接口
 * 
 * @author system
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    @Autowired
    private EvaluationService evaluationService;

    /**
     * 根据openid获取用户信息
     * 
     * @param openid 用户openid
     * @return 用户信息
     */
    @GetMapping("/getMemberInfoByOpenid")
    public AgentResponse getMemberInfoByOpenid(@RequestParam String openid) {
        log.info("获取用户信息请求：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return evaluationService.getMemberInfoByOpenid(openid);

        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return AgentResponse.fail("获取用户信息异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取用户问卷答案
     * 
     * @param openid 用户openid
     * @return 问卷答案
     */
    @GetMapping("/getAnswer")
    public AgentResponse getAnswer(@RequestParam String openid) {
        log.info("获取用户问卷答案请求：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return evaluationService.getAnswer(openid);

        } catch (Exception e) {
            log.error("获取用户问卷答案异常", e);
            return AgentResponse.fail("获取用户问卷答案异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取用户评测信息
     * 
     * @param openid 用户openid（也可以是手机号）
     * @return 评测信息
     */
    @GetMapping("/getEvaluationByOpenid")
    public AgentResponse getEvaluationByOpenid(@RequestParam String openid) {
        log.info("获取用户评测信息请求：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return evaluationService.getEvaluationByOpenid(openid);

        } catch (Exception e) {
            log.error("获取用户评测信息异常", e);
            return AgentResponse.fail("获取用户评测信息异常：" + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public AgentResponse health() {
        return AgentResponse.success("评测服务正常运行");
    }
}
