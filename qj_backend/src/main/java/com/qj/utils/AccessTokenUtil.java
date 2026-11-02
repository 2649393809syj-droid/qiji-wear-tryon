package com.qj.utils;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 微信AccessToken工具类
 * 
 * @author system
 */
@Component
public class AccessTokenUtil {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenUtil.class);

    // 添加锁来防止并发获取token
    private static final ReentrantLock lock = new ReentrantLock();

    @Value("${wx.miniapp.appid}")
    private String appId;

    @Value("${wx.miniapp.secret}")
    private String secret;

    @Value("${wx.miniapp.env}")
    private String env;

    private static String staticAppId;
    private static String staticSecret;
    private static String staticEnv;

    private static String accessToken;
    private static long tokenExpireTime = 0;
    private static boolean tokenValidFlag = false;

    // 最后一次获取token的时间，用于防止频繁刷新
    private static long lastRefreshTime = 0;

    // 最小刷新间隔（10秒）
    private static final long MIN_REFRESH_INTERVAL = 10 * 1000L;

    @PostConstruct
    public void init() {
        staticAppId = appId;
        staticSecret = secret;
        staticEnv = env;
    }

    /**
     * 获取AccessToken
     * 
     * @return AccessToken
     */
    public static String getAccessToken() {
        return getAccessToken(false);
    }

    /**
     * 获取AccessToken，支持强制刷新
     * 
     * @param forceRefresh 是否强制刷新
     * @return AccessToken
     */
    public static String getAccessToken(boolean forceRefresh) {
        // 如果token有效且未强制刷新，直接返回
        if (!forceRefresh && tokenValidFlag && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        lock.lock();
        try {
            // 双重检查锁定，防止重复获取
            if (!forceRefresh && tokenValidFlag && System.currentTimeMillis() < tokenExpireTime) {
                return accessToken;
            }

            // 防止频繁刷新
            long currentTime = System.currentTimeMillis();
            if (!forceRefresh && (currentTime - lastRefreshTime) < MIN_REFRESH_INTERVAL) {
                log.warn("AccessToken刷新过于频繁，跳过本次刷新");
                return accessToken;
            }

            return refreshAccessToken();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 刷新AccessToken，带重试机制
     * 
     * @return AccessToken
     */
    private static String refreshAccessToken() {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                        + staticAppId + "&secret=" + staticSecret;

                log.info("正在获取AccessToken，重试次数：{}/{}", retryCount + 1, maxRetries);

                String response = HttpUtil.get(url, 10000); // 增加超时时间到10秒
                JSONObject jsonObject = JSONUtil.parseObj(response);

                if (jsonObject.containsKey("access_token")) {
                    String newAccessToken = jsonObject.getStr("access_token");
                    int expiresIn = jsonObject.getInt("expires_in", 7200);

                    // 设置过期时间，提前10分钟刷新
                    long newExpireTime = System.currentTimeMillis() + (expiresIn - 600) * 1000L;

                    // 更新静态变量
                    accessToken = newAccessToken;
                    tokenExpireTime = newExpireTime;
                    tokenValidFlag = true;
                    lastRefreshTime = System.currentTimeMillis();

                    log.info("获取AccessToken成功，有效期至：{}", new java.util.Date(newExpireTime));
                    return accessToken;
                } else {
                    String errorCode = jsonObject.getStr("errcode");
                    String errorMsg = jsonObject.getStr("errmsg");
                    log.error("获取AccessToken失败：errcode={}, errmsg={}", errorCode, errorMsg);

                    // 如果是参数错误（40013、40001等），不需要重试
                    if ("40013".equals(errorCode) || "40001".equals(errorCode)) {
                        log.error("AppId或Secret配置错误，停止重试");
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("获取AccessToken异常，重试次数：{}/{}", retryCount + 1, maxRetries, e);
            }

            retryCount++;
            if (retryCount < maxRetries) {
                try {
                    // 指数退避：第一次等待1秒，第二次等待2秒
                    Thread.sleep(retryCount * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("获取AccessToken失败，已重试{}次", maxRetries);
        tokenValidFlag = false;
        return null;
    }

    /**
     * 当接口返回40001错误时，立即刷新token
     * 
     * @return 新的AccessToken
     */
    public static String forceRefreshAccessToken() {
        log.warn("检测到AccessToken失效，强制刷新");
        return getAccessToken(true);
    }

    /**
     * 检查AccessToken是否即将过期（5分钟内）
     * 
     * @return 是否即将过期
     */
    public static boolean isTokenExpiringSoon() {
        return System.currentTimeMillis() > (tokenExpireTime - 300 * 1000L);
    }

    /**
     * 获取微信云开发环境ID
     * 
     * @return 环境ID
     */
    public static String getEnv() {
        return staticEnv;
    }

    /**
     * 获取AppId
     * 
     * @return AppId
     */
    public static String getAppId() {
        return staticAppId;
    }
}