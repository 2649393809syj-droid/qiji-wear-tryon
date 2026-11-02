package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.XPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 小程序虚拟支付（xpay / 代币钱包）接口。路径前缀 /qj/xpay。
 *
 * 阶段1服务于 AI 试穿付费：
 *  - /balance      查代币余额
 *  - /rechargeSign 取 wx.requestVirtualPayment 充值签名
 *  - /spend        扣代币发权益（余额不足返回 needRecharge）
 *  - /coinPayNotify 微信充值到账回调
 *  - /refund       预留（阶段2/3）
 *
 * 未开通时由 XPayService 统一走友好拒绝分支。
 */
@RestController
@RequestMapping("/qj/xpay")
public class XPayController {

    private static final Logger log = LoggerFactory.getLogger(XPayController.class);

    @Autowired
    private XPayService xPayService;

    @PostMapping("/balance")
    public AgentResponse balance(@RequestBody String requestBody, HttpServletRequest request) {
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            return xPayService.getBalance(req.getStr("openid"), req.getStr("code"), clientIp(request));
        } catch (Exception e) {
            log.error("xpay/balance 异常", e);
            return AgentResponse.fail("查询余额异常：" + e.getMessage());
        }
    }

    @PostMapping("/rechargeSign")
    public AgentResponse rechargeSign(@RequestBody String requestBody, HttpServletRequest request) {
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            int amount = req.getInt("amount", 0);
            return xPayService.buildRechargeSign(
                    req.getStr("openid"), req.getStr("code"), amount, req.getStr("platform"));
        } catch (Exception e) {
            log.error("xpay/rechargeSign 异常", e);
            return AgentResponse.fail("生成充值签名异常：" + e.getMessage());
        }
    }

    @PostMapping("/spend")
    public AgentResponse spend(@RequestBody String requestBody, HttpServletRequest request) {
        log.info("xpay/spend 请求: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            return xPayService.spend(req.getStr("openid"), req.getStr("code"), req.getStr("kind"),
                    clientIp(request), req.getStr("platform"));
        } catch (Exception e) {
            log.error("xpay/spend 异常", e);
            return AgentResponse.fail("扣费异常：" + e.getMessage());
        }
    }

    /**
     * 通用扣代币（阶段2+）：任意金额、外部 billNo（业务订单 tradeNo），只扣不发权益。
     * 扣费成功后由前端照常调各业务原有"支付成功"接口发权益。
     */
    @PostMapping("/spendAmount")
    public AgentResponse spendAmount(@RequestBody String requestBody, HttpServletRequest request) {
        log.info("xpay/spendAmount 请求: {}", requestBody);
        try {
            JSONObject req = JSONUtil.parseObj(requestBody);
            int amount = req.getInt("amount", 0);
            return xPayService.spendAmount(
                    req.getStr("openid"), req.getStr("code"), amount, req.getStr("billNo"),
                    clientIp(request), req.getStr("platform"));
        } catch (Exception e) {
            log.error("xpay/spendAmount 异常", e);
            return AgentResponse.fail("扣费异常：" + e.getMessage());
        }
    }

    /** 微信充值到账回调。需返回 {"errcode":0,"errmsg":"ok"} 形式的 ack。 */
    @PostMapping("/coinPayNotify")
    public String coinPayNotify(@RequestBody(required = false) String rawBody) {
        log.info("xpay/coinPayNotify 收到回调");
        boolean ok = xPayService.handleCoinPayNotify(StrUtil.nullToEmpty(rawBody));
        return ok ? "{\"errcode\":0,\"errmsg\":\"ok\"}"
                  : "{\"errcode\":-1,\"errmsg\":\"fail\"}";
    }

    /** 退款，预留（阶段2/3 接入）。 */
    @PostMapping("/refund")
    public AgentResponse refund(@RequestBody String requestBody) {
        return AgentResponse.fail("退款接口未开放");
    }

    /**
     * 取真实客户端 IP：优先 X-Forwarded-For（逗号分隔取第一个、trim），
     * 为空再取 X-Real-IP，再为空取 remoteAddr；结果为空兜底 127.0.0.1。
     */
    private static String clientIp(HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String xff = req.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff)) {
            String first = xff.split(",")[0].trim();
            if (StrUtil.isNotBlank(first)) return first;
        }
        String realIp = req.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) return realIp.trim();
        String remote = req.getRemoteAddr();
        return StrUtil.isNotBlank(remote) ? remote : "127.0.0.1";
    }
}
