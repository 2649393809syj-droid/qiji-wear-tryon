package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import com.qj.db.DBConfig;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用通用配置加载器。启动时从 qj_app_config 表读取，写入内存 cache。
 * 修改 DB 配置后**需要重启**才生效（本期不做热 reload）。
 *
 * 当前管理：
 *  - 微信支付凭据（v2-key / mchid / appid / orderquery url）
 *  - Ark / Doubao Seedream API Key
 *
 * 表结构见 migration_app_config.sql。
 */
@Service
public class AppConfigService {

    private static final Logger log = LoggerFactory.getLogger(AppConfigService.class);
    private static final String TABLE = "qj_app_config";

    public static final String KEY_V2_KEY         = "wxpay_v2_key";
    public static final String KEY_MCHID          = "wxpay_mchid";
    public static final String KEY_APPID          = "wxpay_appid";
    public static final String KEY_ORDERQUERY_URL = "wxpay_orderquery_url";
    public static final String KEY_ARK_API_KEY    = "ark_api_key";
    // AI 试穿分项定价（单位：分）。缺省时用兜底默认值，不计入必填校验。
    public static final String KEY_GENERATE_PRICE = "ai_tryon_generate_price";
    public static final String KEY_UPLOAD_PRICE   = "ai_tryon_upload_price";
    private static final int DEFAULT_GENERATE_PRICE = 300; // 试穿 ¥3
    private static final int DEFAULT_UPLOAD_PRICE   = 100; // 换人像 ¥1

    // 小程序虚拟支付（xpay / 代币钱包）配置。开通前全部为占位符，开通后回填并重启。
    // 注：1 金币 = 1 分（¥0.01），消费金额直接复用现有以"分"计价的字段。
    public static final String KEY_XPAY_OFFER_ID      = "xpay_offer_id";       // 虚拟支付后台的 OfferID
    public static final String KEY_XPAY_APP_KEY       = "xpay_app_key";        // 虚拟支付 AppKey（签名用，敏感）
    public static final String KEY_XPAY_ENV           = "xpay_env";            // 0=正式 1=沙箱
    public static final String KEY_XPAY_CURRENCY_TYPE = "xpay_currency_type";  // 代币类型，缺省 CNY
    public static final String KEY_WX_APP_SECRET      = "wx_app_secret";       // 小程序 appsecret（code2session / access_token 用，敏感）
    private static final String DEFAULT_CURRENCY_TYPE = "CNY";

    private static final String ARK_PLACEHOLDER = "__REPLACE_ME__";
    // xpay 占位符：命中任一则视为"未开通"，相关接口直接友好拒绝、绝不调微信侧
    private static final String XPAY_OFFER_PLACEHOLDER = "PLACEHOLDER_OFFERID";
    private static final String XPAY_KEY_PLACEHOLDER   = "PLACEHOLDER_APPKEY";

    private volatile Map<String, String> cache = Collections.emptyMap();

    private Db getDb() {
        return DBConfig.getDb(null, "qj");
    }

    @PostConstruct
    public void load() {
        try {
            List<Entity> rows = getDb().query("SELECT config_key, config_value FROM " + TABLE);
            Map<String, String> m = new HashMap<>();
            for (Entity row : rows) {
                m.put(row.getStr("config_key"), row.getStr("config_value"));
            }
            this.cache = m;
            log.info("AppConfigService loaded: mchid={} appid={} v2Key={} orderqueryUrl={} arkApiKey={}",
                    m.get(KEY_MCHID),
                    m.get(KEY_APPID),
                    maskKey(m.get(KEY_V2_KEY)),
                    m.get(KEY_ORDERQUERY_URL),
                    maskKey(m.get(KEY_ARK_API_KEY)));
            log.info("AppConfigService xpay: offerId={} appKey={} env={} currency={} appSecret={} configured={}",
                    m.get(KEY_XPAY_OFFER_ID),
                    maskKey(m.get(KEY_XPAY_APP_KEY)),
                    m.get(KEY_XPAY_ENV),
                    StrUtil.blankToDefault(m.get(KEY_XPAY_CURRENCY_TYPE), DEFAULT_CURRENCY_TYPE),
                    maskKey(m.get(KEY_WX_APP_SECRET)),
                    isXpayConfigured());
            for (String required : new String[]{
                    KEY_V2_KEY, KEY_MCHID, KEY_APPID, KEY_ORDERQUERY_URL, KEY_ARK_API_KEY}) {
                if (StrUtil.isBlank(m.get(required))) {
                    log.error("AppConfigService 缺少必填配置: {}（请在 {} 表插入对应行）",
                            required, TABLE);
                }
            }
            if (ARK_PLACEHOLDER.equals(m.get(KEY_ARK_API_KEY))) {
                log.error("AppConfigService: ark_api_key 仍是占位符 {}，请执行 UPDATE qj_app_config "
                        + "SET config_value='<真值>' WHERE config_key='ark_api_key'", ARK_PLACEHOLDER);
            }
        } catch (SQLException e) {
            log.error("AppConfigService 加载失败，所有依赖配置的接口将拒绝处理", e);
        }
    }

    public String getV2Key()         { return cache.get(KEY_V2_KEY); }
    public String getMchId()         { return cache.get(KEY_MCHID); }
    public String getAppId()         { return cache.get(KEY_APPID); }
    public String getOrderqueryUrl() { return cache.get(KEY_ORDERQUERY_URL); }

    /** 返回 ark_api_key；如果仍是占位符 __REPLACE_ME__ 则返回空串避免误用。 */
    public String getArkApiKey() {
        String v = cache.get(KEY_ARK_API_KEY);
        if (StrUtil.isBlank(v) || ARK_PLACEHOLDER.equals(v)) return "";
        return v;
    }

    /** 试穿(生成)单价，单位分；DB 未配置或非法时回退默认 300。 */
    public int getGeneratePrice() { return parsePrice(cache.get(KEY_GENERATE_PRICE), DEFAULT_GENERATE_PRICE); }

    /** 换人像(上传)单价，单位分；DB 未配置或非法时回退默认 100。 */
    public int getUploadPrice() { return parsePrice(cache.get(KEY_UPLOAD_PRICE), DEFAULT_UPLOAD_PRICE); }

    // ===== 虚拟支付（xpay）=====
    public String getXpayOfferId()      { return cache.get(KEY_XPAY_OFFER_ID); }
    public String getXpayAppKey()       { return cache.get(KEY_XPAY_APP_KEY); }
    public String getWxAppSecret() {
        String v = cache.get(KEY_WX_APP_SECRET);
        if (StrUtil.isBlank(v) || ARK_PLACEHOLDER.equals(v)) return "";
        return v;
    }
    public String getXpayCurrencyType() { return StrUtil.blankToDefault(cache.get(KEY_XPAY_CURRENCY_TYPE), DEFAULT_CURRENCY_TYPE); }
    /** 虚拟支付环境：1=沙箱（默认，安全），0=正式。仅当 DB 明确为 0 才走正式。 */
    public int getXpayEnv() {
        String v = cache.get(KEY_XPAY_ENV);
        return "0".equals(StrUtil.trimToEmpty(v)) ? 0 : 1;
    }

    /**
     * 虚拟支付是否已开通可用：OfferID / AppKey / appSecret 三者齐全且非占位符。
     * 任一缺失或仍是占位符 → 视为未开通，相关接口应友好拒绝、绝不调微信侧。
     */
    public boolean isXpayConfigured() {
        String offer = cache.get(KEY_XPAY_OFFER_ID);
        String key = cache.get(KEY_XPAY_APP_KEY);
        String secret = getWxAppSecret();
        if (StrUtil.hasBlank(offer, key) || StrUtil.isBlank(secret)) return false;
        if (XPAY_OFFER_PLACEHOLDER.equals(offer) || XPAY_KEY_PLACEHOLDER.equals(key)) return false;
        return true;
    }

    private static int parsePrice(String v, int fallback) {
        if (StrUtil.isBlank(v)) return fallback;
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String maskKey(String key) {
        if (StrUtil.isBlank(key)) return "(empty)";
        if (ARK_PLACEHOLDER.equals(key)) return "(placeholder __REPLACE_ME__)";
        if (key.length() <= 4) return "***";
        return "***" + key.substring(key.length() - 4);
    }
}
