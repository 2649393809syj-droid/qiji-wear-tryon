package com.qj.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;

import java.nio.charset.StandardCharsets;

/**
 * 小程序虚拟支付（xpay / 代币钱包）签名工具。
 *
 * 微信虚拟支付有两类 HMAC-SHA256 签名（均输出小写 hex）：
 *  - 用 AppKey 签：应用级，证明请求来自本应用。
 *  - 用 session_key 签：用户级，证明操作来自该用户（需 wx.login → code2session 拿 session_key）。
 *
 * 客户端 wx.requestVirtualPayment：
 *  - paySig    = hmac_sha256(AppKey, "requestVirtualPayment&" + signData)
 *  - signature = hmac_sha256(session_key, signData)
 *
 * 服务端 /xpay/* HTTP 接口：
 *  - pay_sig   = hmac_sha256(AppKey, urlPath + '&' + postBody)
 *  - signature = hmac_sha256(session_key, postBody)
 *
 * ⚠️ 精确拼接规则以开通后拿到的官方接入文档 + 沙箱实测为准，落地时在此校准。
 */
public final class XPaySignUtil {
    private XPaySignUtil() {}

    /** 客户端 paySig 固定前缀（官方接入指引要求签名串带此 URI 前缀）。 */
    private static final String CLIENT_PAY_SIG_URI = "requestVirtualPayment";

    public static String randomNonceStr() {
        return RandomUtil.randomString(32);
    }

    /** 通用：HMAC-SHA256(key, message) → 小写 hex。 */
    public static String hmacSha256Hex(String key, String message) {
        return SecureUtil.hmacSha256(key.getBytes(StandardCharsets.UTF_8))
                .digestHex(message == null ? "" : message);
    }

    // ===== 客户端 wx.requestVirtualPayment =====

    /** 客户端 paySig = hmac_sha256(AppKey, "requestVirtualPayment&" + signData)。 */
    public static String clientPaySig(String appKey, String signData) {
        return hmacSha256Hex(appKey, CLIENT_PAY_SIG_URI + "&" + signData);
    }

    /** 客户端 signature = hmac_sha256(session_key, signData)。 */
    public static String clientSignature(String sessionKey, String signData) {
        return hmacSha256Hex(sessionKey, signData);
    }

    // ===== 服务端 /xpay/* HTTP 接口 =====

    /** 服务端 pay_sig = hmac_sha256(AppKey, urlPath + '&' + postBody)。urlPath 形如 "/xpay/currency_pay"。 */
    public static String serverPaySig(String appKey, String urlPath, String postBody) {
        return hmacSha256Hex(appKey, urlPath + "&" + postBody);
    }

    /** 服务端 signature = hmac_sha256(session_key, postBody)。 */
    public static String serverSignature(String sessionKey, String postBody) {
        return hmacSha256Hex(sessionKey, postBody);
    }
}
