package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 微信小程序服务端 API 辅助：access_token 获取/缓存 + jscode2session。
 *
 * access_token 用 **stable_token** 接口获取：它与普通 /cgi-bin/token 共享 token 但不会
 * 互相顶掉，可与现网 GetAccess 云函数并存，不影响其它依赖 access_token 的流程。
 *
 * 凭据（appid / secret）来自 AppConfigService（qj_app_config）。secret 为占位符时一律返回空，
 * 调用方据此判定"虚拟支付未开通"。
 */
@Service
public class WxApiService {

    private static final Logger log = LoggerFactory.getLogger(WxApiService.class);
    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private AppConfigService appConfig;

    private volatile String cachedToken = "";
    private volatile long tokenExpireAt = 0L; // epoch ms

    /**
     * 取 access_token（带内存缓存，提前 5 分钟过期）。失败/未配置返回空串。
     */
    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(cachedToken) && now < tokenExpireAt) {
            return cachedToken;
        }
        String appid = appConfig.getAppId();
        String secret = appConfig.getWxAppSecret();
        if (StrUtil.hasBlank(appid, secret)) {
            log.warn("getAccessToken: appid/secret 未配置，返回空");
            return "";
        }
        try {
            JSONObject body = new JSONObject()
                    .set("grant_type", "client_credential")
                    .set("appid", appid)
                    .set("secret", secret)
                    .set("force_refresh", false);
            String resp = HttpUtil.post(STABLE_TOKEN_URL, body.toString(), 10000);
            JSONObject json = JSONUtil.parseObj(resp);
            String token = json.getStr("access_token");
            int expiresIn = json.getInt("expires_in", 0);
            if (StrUtil.isBlank(token)) {
                log.warn("getAccessToken 失败: {}", resp);
                return "";
            }
            cachedToken = token;
            tokenExpireAt = now + Math.max(0, (expiresIn - 300)) * 1000L;
            return token;
        } catch (Exception e) {
            log.warn("getAccessToken 异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 用 wx.login 的 code 换 session_key。返回 null 表示失败/未配置。
     * session_key 是敏感凭据，只在后端用于签名，绝不下发前端。
     */
    public String code2SessionKey(String jsCode) {
        if (StrUtil.isBlank(jsCode)) return null;
        String appid = appConfig.getAppId();
        String secret = appConfig.getWxAppSecret();
        if (StrUtil.hasBlank(appid, secret)) {
            log.warn("code2SessionKey: appid/secret 未配置");
            return null;
        }
        try {
            String url = JSCODE2SESSION_URL
                    + "?appid=" + appid
                    + "&secret=" + secret
                    + "&js_code=" + jsCode
                    + "&grant_type=authorization_code";
            String resp = HttpUtil.get(url, 10000);
            JSONObject json = JSONUtil.parseObj(resp);
            String sessionKey = json.getStr("session_key");
            if (StrUtil.isBlank(sessionKey)) {
                log.warn("code2SessionKey 失败: {}", json.getStr("errmsg", resp));
                return null;
            }
            return sessionKey;
        } catch (Exception e) {
            log.warn("code2SessionKey 异常: {}", e.getMessage());
            return null;
        }
    }
}
