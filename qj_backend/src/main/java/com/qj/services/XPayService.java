package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import com.qj.entities.AgentResponse;
import com.qj.utils.XPaySignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 小程序虚拟支付（xpay / 代币钱包）服务。约定 1 金币 = 1 分（¥0.01）。
 *
 * 三件事：
 *  - getBalance：查代币余额（get_balance）。
 *  - spend：扣代币发权益（currency_pay + AiTryOnService.grantPaidCredit），bill_no 幂等；
 *           余额不足时返回 needRecharge=true，由前端拉起充值后重试。
 *  - buildRechargeSign：为 wx.requestVirtualPayment 代币充值生成 signData/paySig/signature，
 *           充值额对齐苹果固定档位（iOS 强制、安卓也统一用档位，简化对账）。
 *  - handleCoinPayNotify：充值到账回调（xpay_coin_pay_notify）对账。
 *
 * 安全闸门：appConfig.isXpayConfigured()（OfferID/AppKey/appSecret 任一为占位符即 false）。
 * 未开通时所有对外方法走友好拒绝分支，绝不调用微信侧——确保开通前可编译、可部署、不误触。
 *
 * ⚠️ xpay HTTP 接口的精确字段名/拼接/错误码以**开通后官方接入文档 + 沙箱实测**为准，
 *    本类已锁定数据流与签名骨架，落地沙箱时按文档校准 buildSpendBody / buildRechargeSignData 等处。
 */
@Service
public class XPayService {

    private static final Logger log = LoggerFactory.getLogger(XPayService.class);

    private static final String XPAY_BASE = "https://api.weixin.qq.com";
    private static final String PATH_GET_BALANCE  = "/xpay/query_user_balance";
    private static final String PATH_CURRENCY_PAY = "/xpay/currency_pay";

    /**
     * 代币充值固定档位（单位：分 = 金币，升序）。
     * 档位与实际在售虚拟商品价格对齐（消费级 ≤¥998）：
     *   ¥1(AI换人像/衣橱·搭配充值1次)、¥3(AI生成)、¥5(衣橱·搭配充值)、¥9.9(兼容旧档位)、¥19.9(形象测试)、
     *   ¥10(充值15次)、¥50/¥99/¥199(游戏杀)、¥365(智能穿搭会员/服务卡)、
     *   ¥398(课程/测试报告)、¥399(衣橱次卡)、¥998(衣橱年卡)。
     * alignToTier 向上取整到 ≥ 需求额的最近档位。
     * 高价商品(平台代理¥2000/城市代理¥5000/顾问项目¥1980·¥2980/定制套餐¥8800·¥19800)本轮未配——
     *   超苹果 IAP 上限，走线下，不经虚拟支付。
     * iOS 上线时这些档位需在 App Store Connect 配对应 IAP 商品
     *   （苹果只支持固定价点，¥365/¥398/¥399 等需就近取苹果价点）。
     */
    private static final int[] APPLE_PRICE_TIERS = {
            100, 300, 500, 990, 1000, 1990, 5000, 9900, 19900, 36500, 39800, 39900, 99800
    };

    /** 微信虚拟支付"余额不足"错误码。 */
    // 268490006=余额不足(Go SDK)；社区另有 -15006 系列说法，沙箱实测确认真实返回码
    private static final int ERRCODE_INSUFFICIENT_BALANCE = 268490006;

    /** 业务订单表（衣橱/服务卡/会员升级/顾问咨询/报告等经 orderPay 下单的订单都落这张表）。 */
    private static final String SHOP_ORDER_TABLE = "qj_shop_order";

    @Autowired
    private AppConfigService appConfig;

    @Autowired
    private WxApiService wxApiService;

    @Autowired
    private AiTryOnService aiTryOnService;

    private boolean ready() {
        return appConfig.isXpayConfigured();
    }

    private AgentResponse notOpened() {
        return AgentResponse.fail("虚拟支付尚未开通，请稍后再试（管理员：开通并回填 OfferID/AppKey/appsecret 后重启）");
    }

    private Db getDb() {
        return DBConfig.getDb(null, "qj");
    }

    /**
     * 凭业务订单号反查服务端权威金额（分），用于扣代币硬化：绝不信任前端传入的金额。
     * 命中 qj_shop_order(trade_no=billNo, openid) 时返回 amount_due（应付，分）；
     * 未命中返回 null（调用方决定是回退还是拒绝）。
     */
    private Integer resolveOrderAmountCents(String openid, String billNo) {
        if (StrUtil.hasBlank(openid, billNo)) return null;
        try {
            Entity order = getDb().queryOne(
                    "SELECT amount_due, total, pay_status FROM " + SHOP_ORDER_TABLE
                            + " WHERE trade_no = ? AND openid = ? LIMIT 1",
                    billNo, openid);
            if (order == null) return null;
            // 优先用应付额 amount_due，缺失时回退 total
            Integer due = order.getInt("amount_due");
            if (due == null || due <= 0) due = order.getInt("total");
            if (due == null || due <= 0) {
                log.warn("resolveOrderAmountCents 命中订单但金额非法 billNo={} openid={} amount_due/total<=0", billNo, openid);
                return null;
            }
            return due;
        } catch (Exception e) {
            log.error("resolveOrderAmountCents 反查订单异常 billNo={} openid={}", billNo, openid, e);
            return null;
        }
    }

    // ===================== 查余额 =====================

    /**
     * 查询用户代币余额（金币）。需要 wx.login 的 code 换 session_key 做用户级签名。
     */
    public AgentResponse getBalance(String openid, String code, String userIp) {
        if (StrUtil.isBlank(openid)) return AgentResponse.fail("参数错误：openid 不能为空");
        if (!ready()) {
            // 未开通：返回余额 0，前端据此走"需充值"提示而不报错
            return AgentResponse.success("虚拟支付未开通", new JSONObject().set("balance", 0).set("xpayReady", false));
        }
        String sessionKey = wxApiService.code2SessionKey(code);
        if (sessionKey == null) return AgentResponse.fail("登录态校验失败，请重试");
        try {
            JSONObject body = baseBody(openid).set("user_ip", safeIp(userIp));
            JSONObject resp = callXpay(PATH_GET_BALANCE, body, sessionKey);
            int errcode = resp.getInt("errcode", -1);
            if (errcode != 0) {
                log.warn("get_balance 失败 openid={} resp={}", openid, resp);
                return AgentResponse.fail("查询余额失败");
            }
            int balance = resp.getInt("balance", 0);
            return AgentResponse.success("查询成功",
                    new JSONObject().set("balance", balance).set("xpayReady", true));
        } catch (Exception e) {
            log.warn("getBalance 异常 openid={}", openid, e);
            return AgentResponse.fail("查询余额异常");
        }
    }

    // ===================== 扣费发权益 =====================

    /**
     * 扣代币并发放 AI 试穿权益。kind=generate/upload，扣 priceOf(kind) 金币。
     * 余额够 → currency_pay 扣费 → grantPaidCredit 发 1 次；
     * 余额不足 → 返回 needRecharge=true + shortfall（建议充值额，已对齐档位），前端拉起充值后重试。
     */
    public AgentResponse spend(String openid, String code, String kind, String userIp, String platform) {
        if (StrUtil.isBlank(openid)) return AgentResponse.fail("参数错误：openid 不能为空");
        if (!ready()) return notOpened();

        int amount = aiTryOnService.unitPriceOf(kind); // 金币 = 分
        String sessionKey = wxApiService.code2SessionKey(code);
        if (sessionKey == null) return AgentResponse.fail("登录态校验失败，请重试");

        String billNo = genBillNo();
        CurrencyPayOutcome out = currencyPay(openid, sessionKey, amount, billNo, safeIp(userIp), deviceTypeOf(platform));
        if (out.insufficient()) {
            int suggest = alignToTier(amount);
            log.info("spend 余额不足，建议充值 openid={} need={} suggest={}", openid, amount, suggest);
            return AgentResponse.success("余额不足，请充值", new JSONObject()
                    .set("needRecharge", true)
                    .set("shortfall", amount)
                    .set("suggestRecharge", suggest));
        }
        if (!out.ok()) {
            return AgentResponse.fail("扣费失败，请重试");
        }

        // 扣费成功 → 发权益（billNo 幂等）
        boolean granted = aiTryOnService.grantPaidCredit(openid, kind, billNo, out.txId);
        if (!granted) {
            log.error("currency_pay 已扣但发权益失败！openid={} billNo={} kind={} —— 需人工补单", openid, billNo, kind);
            return AgentResponse.fail("支付成功但发放异常，请联系客服（单号 " + billNo + "）");
        }
        return AgentResponse.success("购买成功", new JSONObject()
                .set("needRecharge", false)
                .set("billNo", billNo)
                .set("paidRemaining", aiTryOnService.paidRemainingOf(openid, kind)));
    }

    /**
     * 通用扣代币（阶段2+）：任意金额、外部传入 billNo（一般用业务订单的 tradeNo 做幂等键），
     * **只扣代币、不发权益**——发权益沿用各业务原有的"支付成功后"接口（前端在扣费成功后照常调）。
     * 与微信侧 bill_no 幂等天然对齐：同一 billNo 不会重复扣款。
     */
    public AgentResponse spendAmount(String openid, String code, int claimedAmountCents, String billNo,
                                     String userIp, String platform) {
        if (StrUtil.isBlank(openid)) return AgentResponse.fail("参数错误：openid 不能为空");
        if (StrUtil.isBlank(billNo)) return AgentResponse.fail("参数错误：billNo 不能为空");
        if (!ready()) return notOpened();

        // —— 金额硬化：服务端凭 billNo 反查订单真实金额，不信任前端传入的 claimedAmountCents ——
        int amountCents;
        Integer serverAmount = resolveOrderAmountCents(openid, billNo);
        if (serverAmount != null) {
            amountCents = serverAmount;
            if (claimedAmountCents != serverAmount) {
                // 前端金额与订单不符：可能是篡改或下单后改价，一律以服务端订单金额为准
                log.warn("spendAmount 金额不一致，以订单为准 billNo={} openid={} 前端={} 订单={}",
                        billNo, openid, claimedAmountCents, serverAmount);
            }
        } else {
            // 未命中 qj_shop_order（例如代理 applyAgent：扣费前未建单、真实价在角色表）。
            // TODO(代理硬化): 正式定价上线前，改为按角色表 current_price/renew_price 服务端定价后再扣，
            //   彻底去除对前端金额的信任。当前保持原行为但打 warn，避免回归。
            if (claimedAmountCents <= 0) return AgentResponse.fail("参数错误：金额非法");
            amountCents = claimedAmountCents;
            log.warn("spendAmount 未命中订单、暂用前端金额（非订单类流程，待硬化）billNo={} openid={} amount={}",
                    billNo, openid, claimedAmountCents);
        }

        String sessionKey = wxApiService.code2SessionKey(code);
        if (sessionKey == null) return AgentResponse.fail("登录态校验失败，请重试");

        CurrencyPayOutcome out = currencyPay(openid, sessionKey, amountCents, billNo, safeIp(userIp), deviceTypeOf(platform));
        if (out.insufficient()) {
            int suggest = alignToTier(amountCents);
            log.info("spendAmount 余额不足 openid={} need={} suggest={} billNo={}", openid, amountCents, suggest, billNo);
            return AgentResponse.success("余额不足，请充值", new JSONObject()
                    .set("needRecharge", true)
                    .set("shortfall", amountCents)
                    .set("suggestRecharge", suggest));
        }
        if (!out.ok()) {
            return AgentResponse.fail("扣费失败，请重试");
        }
        return AgentResponse.success("扣费成功", new JSONObject()
                .set("needRecharge", false)
                .set("billNo", billNo)
                .set("transactionId", out.txId));
    }

    /** 调一次 currency_pay 扣代币。封装错误码判定，供 spend / spendAmount 复用。 */
    private CurrencyPayOutcome currencyPay(String openid, String sessionKey, int amount, String billNo,
                                           String userIp, int deviceType) {
        try {
            JSONObject body = baseBody(openid)
                    .set("order_id", billNo)
                    .set("amount", amount)
                    .set("user_ip", userIp)
                    .set("device_type", deviceType)
                    .set("payitem", buildPayItem(amount));
            JSONObject resp = callXpay(PATH_CURRENCY_PAY, body, sessionKey);
            int errcode = resp.getInt("errcode", -1);
            if (errcode == ERRCODE_INSUFFICIENT_BALANCE) {
                return new CurrencyPayOutcome(PAY_INSUFFICIENT, null);
            }
            if (errcode != 0) {
                log.warn("currency_pay 失败 openid={} billNo={} resp={}", openid, billNo, resp);
                return new CurrencyPayOutcome(PAY_ERROR, null);
            }
            String txId = resp.getStr("order_id", resp.getStr("transaction_id", billNo));
            return new CurrencyPayOutcome(PAY_OK, txId);
        } catch (Exception e) {
            log.error("currency_pay 异常 openid={} billNo={}", openid, billNo, e);
            return new CurrencyPayOutcome(PAY_ERROR, null);
        }
    }

    private static final int PAY_OK = 0, PAY_INSUFFICIENT = 1, PAY_ERROR = -1;

    private static final class CurrencyPayOutcome {
        final int status;
        final String txId;
        CurrencyPayOutcome(int status, String txId) { this.status = status; this.txId = txId; }
        boolean ok() { return status == PAY_OK; }
        boolean insufficient() { return status == PAY_INSUFFICIENT; }
    }

    // ===================== 充值签名 =====================

    /**
     * 为 wx.requestVirtualPayment（代币充值）生成 signData/paySig/signature。
     * @param amountCents 期望充值额（分=金币）；后端对齐到 ≥ 该值的最近苹果档位后签名。
     * @param platform    "ios" / "android"（影响档位强制度，签名按对齐后的额）。
     */
    public AgentResponse buildRechargeSign(String openid, String code, int amountCents, String platform) {
        if (StrUtil.isBlank(openid)) return AgentResponse.fail("参数错误：openid 不能为空");
        if (!ready()) return notOpened();

        String sessionKey = wxApiService.code2SessionKey(code);
        if (sessionKey == null) return AgentResponse.fail("登录态校验失败，请重试");

        int tierAmount = alignToTier(amountCents);
        String plat = "ios".equalsIgnoreCase(platform) ? "ios" : "android";
        String billNo = genBillNo();

        // signData：客户端 wx.requestVirtualPayment 的入参原文（字段名待沙箱校准）
        JSONObject signDataObj = new JSONObject()
                .set("offerId", appConfig.getXpayOfferId())
                .set("buyQuantity", tierAmount)        // 充值代币数量（金币）
                .set("env", appConfig.getXpayEnv())
                .set("currencyType", appConfig.getXpayCurrencyType())
                .set("platform", plat)
                .set("zoneId", "1")
                .set("outTradeNo", billNo)
                .set("attach", billNo);     // 透传字段，回调原样带回，用 billNo 便于对账
        String signData = signDataObj.toString();

        String appKey = appConfig.getXpayAppKey();
        String paySig = XPaySignUtil.clientPaySig(appKey, signData);
        String signature = XPaySignUtil.clientSignature(sessionKey, signData);

        return AgentResponse.success("ok", new JSONObject()
                .set("signData", signData)
                .set("paySig", paySig)
                .set("signature", signature)
                .set("mode", "short_series_coin")     // 代币模式
                .set("amount", tierAmount)
                .set("billNo", billNo));
    }

    // ===================== 充值到账回调 =====================

    /**
     * 处理 xpay_coin_pay_notify（充值到账）。阶段1仅做验签 + 对账日志，不自建本地账本
     * （代币余额以微信侧为准）。返回是否处理成功（决定回 ack）。
     */
    public boolean handleCoinPayNotify(String rawBody) {
        if (!ready()) {
            log.warn("收到 coinPayNotify 但虚拟支付未开通，忽略");
            return false;
        }
        try {
            JSONObject payload = JSONUtil.parseObj(rawBody);
            // TODO 沙箱校准：按官方文档验签（pay_sig / signature），再对账
            log.info("coinPayNotify 到账: {}", payload);
            return true;
        } catch (Exception e) {
            log.warn("coinPayNotify 解析失败: {}", e.getMessage());
            return false;
        }
    }

    // ===================== 内部 =====================

    /** 公共请求体字段（offer_id / env / openid / ts / currency_type）。 */
    // TODO(沙箱校准 Tier-2): 官方 body 仅 {openid,env,user_ip}；offer_id/currency_type/ts/zone_id 是否需要/是否应在 URL 待沙箱实测，多余字段微信一般忽略
    private JSONObject baseBody(String openid) {
        return new JSONObject()
                .set("openid", openid)
                .set("offer_id", appConfig.getXpayOfferId())
                .set("env", appConfig.getXpayEnv())
                .set("currency_type", appConfig.getXpayCurrencyType())
                .set("ts", System.currentTimeMillis() / 1000)
                .set("zone_id", "1");
    }

    /** 平台 → device_type：iOS=2，其它（android/devtools 等）=1。 */
    static int deviceTypeOf(String platform) {
        return "ios".equalsIgnoreCase(platform) ? 2 : 1;
    }

    /** 客户端 IP 兜底：为空时回退 127.0.0.1，避免 currency_pay 缺 user_ip。 */
    private static String safeIp(String userIp) {
        return StrUtil.isNotBlank(userIp) ? userIp : "127.0.0.1";
    }

    /** 构造 currency_pay 的 payitem JSON 字符串：[{"productid":"coin","unit_price":<amount>,"quantity":1}]。 */
    private static String buildPayItem(int amount) {
        JSONObject item = new JSONObject()
                .set("productid", "coin")
                .set("unit_price", amount)
                .set("quantity", 1);
        return JSONUtil.createArray().set(item).toString();
    }

    /**
     * 调用 xpay 服务端接口：access_token 走 URL，pay_sig（AppKey 签）+ signature（session_key 签）走 query。
     * 抛异常或 errcode!=0 由调用方处理。
     *
     * TODO(沙箱校准 Tier-2)：以下几项需开通后沙箱实测确认——
     *   ① pay_sig/signature 放 URL query 还是 POST body（Go SDK 倾向 body，现代码放 URL query）；
     *   ② pay_sig 签名 uri 是否只用 path（不含 access_token 等 query）；
     *   ③ buildRechargeSign 的 signData 字段（offerId/buyQuantity/env/...）是否符合 wx.requestVirtualPayment 代币模式官方格式；
     *   ④ handleCoinPayNotify 的事件名与验签。
     */
    private JSONObject callXpay(String path, JSONObject body, String sessionKey) {
        String accessToken = wxApiService.getAccessToken();
        if (StrUtil.isBlank(accessToken)) {
            throw new IllegalStateException("access_token 获取失败");
        }
        String postBody = body.toString();
        String appKey = appConfig.getXpayAppKey();
        String paySig = XPaySignUtil.serverPaySig(appKey, path, postBody);
        String signature = XPaySignUtil.serverSignature(sessionKey, postBody);
        String url = XPAY_BASE + path
                + "?access_token=" + accessToken
                + "&pay_sig=" + paySig
                + "&signature=" + signature;
        String resp = HttpUtil.post(url, postBody, 10000);
        // 沙箱期完整打出响应，便于核对真实 errcode/字段名
        log.info("callXpay resp path={} resp={}", path, resp);
        return JSONUtil.parseObj(resp);
    }

    /** 充值额对齐到 ≥ need 的最近苹果档位；超过最大档位则取最大档（会告警，可能导致少充）。 */
    static int alignToTier(int need) {
        for (int tier : APPLE_PRICE_TIERS) {
            if (tier >= need) return tier;
        }
        int max = APPLE_PRICE_TIERS[APPLE_PRICE_TIERS.length - 1];
        log.warn("alignToTier 封顶：需充 {} 分超过最大档位 {} 分，按最大档返回（用户可能需多次充值）", need, max);
        return max;
    }

    private static String genBillNo() {
        return "XP" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
