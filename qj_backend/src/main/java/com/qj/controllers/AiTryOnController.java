package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.AiTryOnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 虚拟试穿接口。路径前缀 /qj/aiTryOn。
 */
@RestController
@RequestMapping("/qj/aiTryOn")
public class AiTryOnController {

    private static final Logger log = LoggerFactory.getLogger(AiTryOnController.class);

    @Autowired
    private AiTryOnService aiTryOnService;

    @PostMapping("/generate")
    public AgentResponse generate(@RequestBody String requestBody) {
        log.info("AI 试穿生成请求: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            String openid = req.getStr("openid");
            String humanImageUrl = req.getStr("humanImageUrl");
            Integer humanSource = req.getInt("humanSource");
            Long schemeId = req.getLong("schemeId");
            JSONArray clothingList = req.getJSONArray("clothingList");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid 不能为空");
            }
            return aiTryOnService.generate(openid, humanImageUrl, humanSource, schemeId, clothingList);
        } catch (Exception e) {
            log.error("AI 试穿生成异常", e);
            return AgentResponse.fail("AI 试穿生成异常：" + e.getMessage());
        }
    }

    @GetMapping("/result")
    public AgentResponse result(@RequestParam("taskId") String taskId) {
        return aiTryOnService.getResult(taskId);
    }

    @GetMapping("/history")
    public AgentResponse history(@RequestParam("openid") String openid,
                                 @RequestParam(value = "schemeId", required = false) Long schemeId,
                                 @RequestParam(value = "pageNumber", required = false) Integer pageNumber,
                                 @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return aiTryOnService.getHistory(openid, schemeId, pageNumber, pageSize);
    }

    @GetMapping("/quota")
    public AgentResponse quota(@RequestParam("openid") String openid) {
        return aiTryOnService.getQuota(openid);
    }

    @GetMapping("/_health")
    public AgentResponse health() {
        return aiTryOnService.health();
    }

    @PostMapping("/portrait")
    public AgentResponse portrait(@RequestBody String requestBody) {
        log.info("人像上传请求: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            String openid = req.getStr("openid");
            String imageUrl = req.getStr("imageUrl");
            String cloudFileId = req.getStr("cloudFileId");
            return aiTryOnService.savePortrait(openid, imageUrl, cloudFileId);
        } catch (Exception e) {
            log.error("人像上传异常", e);
            return AgentResponse.fail("人像上传异常：" + e.getMessage());
        }
    }

    @PostMapping("/createOrder")
    public AgentResponse createOrder(@RequestBody String requestBody) {
        log.info("创建 AI 试穿订单: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            String openid = req.getStr("openid");
            String kind = req.getStr("kind"); // generate(试穿) / upload(换人像)，缺省按 generate
            return aiTryOnService.createOrder(openid, kind);
        } catch (Exception e) {
            log.error("创建订单异常", e);
            return AgentResponse.fail("创建订单异常：" + e.getMessage());
        }
    }

    @PostMapping("/markPaid")
    public AgentResponse markPaid(@RequestBody String requestBody) {
        log.info("标记订单已支付: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            String openid = req.getStr("openid");
            String tradeNo = req.getStr("tradeNo");
            return aiTryOnService.markPaid(openid, tradeNo);
        } catch (Exception e) {
            log.error("标记订单异常", e);
            return AgentResponse.fail("标记订单异常：" + e.getMessage());
        }
    }
}
