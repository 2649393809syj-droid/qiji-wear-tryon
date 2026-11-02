package com.qj.services.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * API密钥拦截器
 * 用于验证API请求的AccessKey和SecretKey
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${api.access-key:}")
    private String configuredAccessKey;

    @Value("${api.secret-key:}")
    private String configuredSecretKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的AccessKey和SecretKey
        String accessKey = request.getHeader("X-Access-Key");
        String secretKey = request.getHeader("X-Secret-Key");

        // 检查是否提供了密钥
        if (StrUtil.isBlank(accessKey) || StrUtil.isBlank(secretKey)) {
            handleUnauthorized(response, "缺少API密钥");
            return false;
        }

        // 验证密钥是否正确
        if (!configuredAccessKey.equals(accessKey) || !configuredSecretKey.equals(secretKey)) {
            handleUnauthorized(response, "API密钥无效");
            return false;
        }

        return true;
    }

    /**
     * 处理未授权的请求（HTTP 401）
     */
    private void handleUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(new JSONObject()
                .set("code", 401)
                .set("msg", message)
                .toString());
    }
}