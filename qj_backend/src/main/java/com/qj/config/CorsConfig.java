package com.qj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 允许本地文件和localhost
        config.addAllowedOrigin("file://");
        config.addAllowedOrigin("http://localhost:7077");
        config.addAllowedOrigin("https://localhost:7077");
        config.addAllowedOrigin("https://qjapiv2.foxcodes.cn");
        config.addAllowedOrigin("http://qjapiv2.foxcodes.cn");
        config.addAllowedOrigin("http://localhost");
        config.addAllowedOrigin("http://localhost:5173");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有方法
        config.addAllowedMethod("*");
        // 允许携带认证信息
        config.setAllowCredentials(true);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}