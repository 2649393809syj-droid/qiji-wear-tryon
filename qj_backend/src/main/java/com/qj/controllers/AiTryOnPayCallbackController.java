package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import com.qj.services.AiTryOnService;
import com.qj.services.AppConfigService;
import com.qj.utils.WxPayV2Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 V2 异步通知端点。
 * 路径与 cloudfunctions/aiTryOnPay 默认 notify_url 对齐：/qj/aiTryOn/payNotify
 *
 * 单独 Controller，不放进 AiTryOnController，避免被 JSON 序列化策略包装。
 * 返回 XML 字符串，由 StringHttpMessageConverter 直接写出。
 * v2Key 来源：AppConfigService（启动时从 qj_app_config 表加载）。
 */
@RestController
@RequestMapping("/qj/aiTryOn")
public class AiTryOnPayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(AiTryOnPayCallbackController.class);

    @Autowired
    private AiTryOnService aiTryOnService;

    @Autowired
    private AppConfigService paymentConfig;

    @PostMapping(value = "/payNotify", produces = "application/xml; charset=UTF-8")
    public String payNotify(@RequestBody String xml) {
        String v2Key = paymentConfig.getV2Key();
        log.info("微信支付回调收到: bytes={}", xml == null ? 0 : xml.length());

        Map<String, String> body;
        try {
            body = WxPayV2Util.parseXml(xml);
        } catch (Exception e) {
            log.warn("payNotify 解析 XML 失败: {}", e.getMessage());
            return fail("XML 解析失败");
        }
        if (body.isEmpty()) {
            log.warn("payNotify body 为空");
            return fail("body 为空");
        }

        if (StrUtil.isBlank(v2Key)) {
            log.error("payNotify 拒绝处理：wxpay.v2-key 未配置");
            return fail("服务端配置缺失");
        }
        if (!WxPayV2Util.verifySign(body, v2Key)) {
            log.warn("payNotify 验签失败: tradeNo={}", body.get("out_trade_no"));
            return fail("签名错误");
        }

        if (!"SUCCESS".equals(body.get("return_code"))) {
            log.warn("payNotify return_code 非 SUCCESS: code={} msg={}",
                    body.get("return_code"), body.get("return_msg"));
            return fail("return_code 非 SUCCESS");
        }
        if (!"SUCCESS".equals(body.get("result_code"))) {
            log.warn("payNotify result_code 非 SUCCESS: code={} err={}",
                    body.get("result_code"), body.get("err_code"));
            return fail("result_code 非 SUCCESS");
        }

        String tradeNo = body.get("out_trade_no");
        String transactionId = body.get("transaction_id");
        if (StrUtil.isBlank(tradeNo)) {
            log.warn("payNotify 缺 out_trade_no");
            return fail("缺 out_trade_no");
        }

        int totalFee = parseIntSafe(body.get("total_fee"));

        boolean ok = aiTryOnService.markPaidFromCallback(tradeNo, transactionId, totalFee);
        if (!ok) {
            return fail("内部处理失败");
        }
        return success();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    private String success() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("return_code", "SUCCESS");
        m.put("return_msg", "OK");
        return WxPayV2Util.toXml(m);
    }

    private String fail(String msg) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("return_code", "FAIL");
        m.put("return_msg", msg);
        return WxPayV2Util.toXml(m);
    }
}
