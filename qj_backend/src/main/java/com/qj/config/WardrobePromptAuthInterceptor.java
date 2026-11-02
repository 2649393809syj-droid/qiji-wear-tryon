package com.qj.config;

import com.qj.services.WardrobePromptAuthService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Protects Prompt management and its paid Gemini operations with an advisor identity. */
@Component
public class WardrobePromptAuthInterceptor implements HandlerInterceptor {

    public static final String ACTOR_ATTRIBUTE = "wardrobePromptActor";
    private final WardrobePromptAuthService authService;

    public WardrobePromptAuthInterceptor(WardrobePromptAuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        try {
            String actor = authService.resolveActor(
                    request.getHeader("X-Wardrobe-Advisor-Token"),
                    request.getHeader("X-Wardrobe-Advisor-Openid"));
            request.setAttribute(ACTOR_ATTRIBUTE, actor);
            return true;
        } catch (SecurityException error) {
            int status = authService.isRequired() && error.getMessage().contains("not configured") ? 503 : 401;
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\""
                    + escape(error.getMessage()) + "\"}");
            return false;
        }
    }

    private String escape(String value) {
        return value == null ? "Unauthorized" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
