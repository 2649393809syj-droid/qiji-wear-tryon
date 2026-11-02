package com.qj.config;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Playwright配置类
 * 优化浏览器启动参数和性能设置
 */
@Configuration
public class PlaywrightConfig {
    
    private static final Logger log = LoggerFactory.getLogger(PlaywrightConfig.class);
    
    @Bean
    public Playwright playwright() {
        log.info("初始化Playwright...");
        return Playwright.create();
    }
    
    @Bean
    public Browser browser(Playwright playwright) {
        log.info("启动Chromium浏览器...");
        
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setTimeout(120000) // 2分钟启动超时
            .setArgs(Arrays.asList(
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--disable-accelerated-2d-canvas",
                "--no-first-run",
                "--no-zygote",
                "--disable-gpu",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-features=TranslateUI",
                "--disable-ipc-flooding-protection",
                "--disable-web-security",
                "--disable-features=VizDisplayCompositor",
                "--disable-extensions",
                "--disable-plugins",
                // "--disable-images", // 保留图片加载
                // "--disable-javascript", // 保留JavaScript以确保数据加载
                "--disable-default-apps",
                "--disable-sync",
                "--disable-translate",
                "--hide-scrollbars",
                "--mute-audio",
                "--no-default-browser-check",
                "--no-pings",
                "--single-process", // 单进程模式
                "--disable-background-networking",
                "--disable-background-timer-throttling",
                "--disable-client-side-phishing-detection",
                "--disable-default-apps",
                "--disable-hang-monitor",
                "--disable-popup-blocking",
                "--disable-prompt-on-repost",
                "--disable-sync",
                "--disable-web-resources",
                "--enable-automation",
                "--enable-logging",
                "--log-level=0",
                "--password-store=basic",
                "--use-mock-keychain",
                "--disable-blink-features=AutomationControlled"
            ));
        
        try {
            Browser browser = playwright.chromium().launch(launchOptions);
            log.info("Chromium浏览器启动成功");
            return browser;
        } catch (Exception e) {
            log.error("Chromium浏览器启动失败: {}", e.getMessage());
            throw new RuntimeException("无法启动Chromium浏览器", e);
        }
    }
    
    @Bean
    public BrowserContext browserContext(Browser browser) {
        log.info("创建浏览器上下文...");
        
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
            .setViewportSize(1920, 1080)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setJavaScriptEnabled(true) // 根据需要启用/禁用JS
            .setAcceptDownloads(false)
            .setIgnoreHTTPSErrors(true); // 忽略HTTPS错误
        
        try {
            BrowserContext context = browser.newContext(contextOptions);
            
            // 设置默认超时
            context.setDefaultTimeout(120000); // 2分钟
            context.setDefaultNavigationTimeout(120000); // 2分钟导航超时
            
            log.info("浏览器上下文创建成功");
            return context;
        } catch (Exception e) {
            log.error("创建浏览器上下文失败: {}", e.getMessage());
            throw new RuntimeException("无法创建浏览器上下文", e);
        }
    }
}
