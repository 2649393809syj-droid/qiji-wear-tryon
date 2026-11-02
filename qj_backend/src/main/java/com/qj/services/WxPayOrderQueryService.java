package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.qj.utils.WxPayV2Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 V2 orderquery 反查服务。
 * 用于 markPaid 接口在标记订单为已付前向微信侧确认实际支付状态，避免前端伪造。
 * 配置来源：AppConfigService（启动时从 qj_app_config 表加载）。
 */
@Service
public class WxPayOrderQueryService {
    private static final Logger log = LoggerFactory.getLogger(WxPayOrderQueryService.class);

    @Autowired
    private AppConfigService paymentConfig;

    public OrderQueryResult queryOrder(String outTradeNo) {
        if (StrUtil.isBlank(outTradeNo)) {
            return OrderQueryResult.unknown("outTradeNo 为空");
        }
        String v2Key = paymentConfig.getV2Key();
        String mchId = paymentConfig.getMchId();
        String appId = paymentConfig.getAppId();
        String orderQueryUrl = paymentConfig.getOrderqueryUrl();
        if (StrUtil.isBlank(v2Key) || StrUtil.isBlank(mchId) || StrUtil.isBlank(appId) || StrUtil.isBlank(orderQueryUrl)) {
            log.warn("wxpay 配置不完整 v2Key={} mchId={} appId={} orderqueryUrl={}",
                    StrUtil.isBlank(v2Key) ? "(empty)" : "***", mchId, appId, orderQueryUrl);
            return OrderQueryResult.unknown("wxpay 配置不完整");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", appId);
        params.put("mch_id", mchId);
        params.put("out_trade_no", outTradeNo);
        params.put("nonce_str", WxPayV2Util.randomNonceStr());
        params.put("sign", WxPayV2Util.sign(params, v2Key));

        String reqXml = WxPayV2Util.toXml(params);
        String respXml;
        try {
            respXml = HttpUtil.post(orderQueryUrl, reqXml, 10000);
        } catch (Exception e) {
            log.warn("调微信 orderquery 失败 outTradeNo={} err={}", outTradeNo, e.getMessage());
            return OrderQueryResult.unknown("orderquery 网络异常: " + e.getMessage());
        }

        Map<String, String> resp;
        try {
            resp = WxPayV2Util.parseXml(respXml);
        } catch (Exception e) {
            log.warn("解析 orderquery 响应失败 outTradeNo={} resp={}", outTradeNo, respXml);
            return OrderQueryResult.unknown("响应解析失败");
        }
        if (resp.isEmpty()) {
            return OrderQueryResult.unknown("响应为空");
        }

        if (!WxPayV2Util.verifySign(resp, v2Key)) {
            log.warn("orderquery 响应验签失败 outTradeNo={}", outTradeNo);
            return OrderQueryResult.unknown("响应验签失败");
        }
        if (!"SUCCESS".equals(resp.get("return_code"))) {
            return OrderQueryResult.unknown(
                    "return_code=" + resp.get("return_code") + " msg=" + resp.get("return_msg"));
        }
        if (!"SUCCESS".equals(resp.get("result_code"))) {
            return OrderQueryResult.unknown(
                    "result_code=" + resp.get("result_code") + " err=" + resp.get("err_code"));
        }
        String tradeState = resp.get("trade_state");
        if (StrUtil.isBlank(tradeState)) {
            return OrderQueryResult.unknown("trade_state 缺失");
        }

        OrderQueryResult r = new OrderQueryResult();
        r.tradeState = tradeState;
        r.transactionId = resp.get("transaction_id");
        r.totalFee = parseIntSafe(resp.get("total_fee"));
        r.message = resp.get("trade_state_desc");
        return r;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    public static class OrderQueryResult {
        /** SUCCESS / REFUND / NOTPAY / CLOSED / REVOKED / USERPAYING / PAYERROR / UNKNOWN */
        public String tradeState;
        public String transactionId;
        public int totalFee;
        public String message;

        public static OrderQueryResult unknown(String msg) {
            OrderQueryResult r = new OrderQueryResult();
            r.tradeState = "UNKNOWN";
            r.message = msg;
            return r;
        }

        public boolean isSuccess() { return "SUCCESS".equals(tradeState); }
        public boolean isPending() { return "NOTPAY".equals(tradeState) || "USERPAYING".equals(tradeState); }
    }
}
