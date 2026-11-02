package com.qj.config;

import cn.hutool.json.JSONConfig;

public class MyJsonConfig {
    private final static String formatStr = "yyyy-MM-dd HH:mm:ss.SSS";      // 日期格式

    /**
     * 获取JSONConfig对象
     * @return
     */
    public static JSONConfig getJsonConfig() {
        JSONConfig config = new JSONConfig();
        config.setDateFormat(formatStr);
        return config;
    }
}
