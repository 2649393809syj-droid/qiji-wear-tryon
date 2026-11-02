package com.qj.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源配置
 * 用于提供二维码图片等静态文件的HTTP访问
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${wx.qrcode.storage.path:qrcode_images}")
    private String qrcodeStoragePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置二维码图片的静态资源映射
        // URL路径：/qrcode_images/**
        // 文件路径：项目根目录/qrcode_images/
        String absolutePath = Paths.get(qrcodeStoragePath).toAbsolutePath().toString();

        registry.addResourceHandler("/" + qrcodeStoragePath + "/**")
                .addResourceLocations("file:" + absolutePath + "/")
                .setCachePeriod(3600 * 24 * 7); // 设置缓存时间：7天
    }
}