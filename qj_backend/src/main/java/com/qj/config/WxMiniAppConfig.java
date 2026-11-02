package com.qj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置类
 * 
 * @author zcs
 * @date 2024-01-01
 */
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMiniAppConfig {

    private String appid;
    private String secret;
    private String env;

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}