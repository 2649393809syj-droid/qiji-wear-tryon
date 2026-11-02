package com.qj.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
        @Autowired
        private JwtAuthInterceptor jwtAuthInterceptor;

        @Autowired
        private WardrobePromptAuthInterceptor wardrobePromptAuthInterceptor;

        private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
                log.info("注册JWT拦截器，只对管理端请求(X-Client-Type: manager)进行权限校验");
                registry.addInterceptor(jwtAuthInterceptor)
                                .addPathPatterns("/**")
                                .excludePathPatterns(
                                                // 静态资源不需要拦截
                                                "/*.html",
                                                "/test_*.html",
                                                "/report_*.html",
                                                "/pages/**",
                                                "/js/**",
                                                "/css/**",
                                                "/img/**",
                                                "/app/**",
                                                "*.js",
                                                "*.css",
                                                "*.png",
                                                "*.jpg",
                                                "*.jpeg",
                                                "*.gif",
                                                "*.ico",
                                                "*.svg",
                                                // 系统错误页面
                                                "/error",
                                                // 认证相关接口不需要拦截
                                                "/auth/login",
                                                "/auth/register");
                registry.addInterceptor(wardrobePromptAuthInterceptor)
                                .addPathPatterns("/qj/wardrobe/prompts/**", "/qj/wardrobe/statistics/**");
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // 配置静态资源映射
                registry.addResourceHandler("/**")
                                .addResourceLocations("classpath:/public/");

                // 确保HTML文件可以被访问
                registry.addResourceHandler("*.html")
                                .addResourceLocations("classpath:/public/");
        }
}
