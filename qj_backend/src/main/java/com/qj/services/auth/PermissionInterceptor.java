package com.qj.services.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 权限拦截器
 * 用于拦截Controller方法，检查用户是否有权限执行
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    /**
     * 处理请求前拦截，检查权限
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 如果不是方法调用，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 检查方法是否有@RequiresPermission注解
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return true; // 没有注解，不需要权限验证
        }

        // 获取用户名
        String username = getCurrentUsername(request);
        if (StrUtil.isBlank(username)) {
            // 用户未登录，返回401
            handleUnauthorized(response, "未登录，无法访问该功能");
            return false;
        }

        // 获取注解中的功能码
        int functionCode = annotation.value();

        // 验证权限
        JSONObject permResult = PermissionService.checkPermission(username, functionCode);
        if (!permResult.getBool("hasPermission", false)) {
            // 用户没有权限，返回403
            if (annotation.strict()) {
                handleForbidden(response, "没有权限执行该功能");
                return false;
            }
        }

        return true;
    }

    /**
     * 从请求中获取当前登录用户名
     */
    private String getCurrentUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object usernameObj = session.getAttribute("username");
            if (usernameObj != null) {
                return usernameObj.toString();
            }
        }
        return null;
    }

    /**
     * 处理未授权的请求（HTTP 401）
     */
    private void handleUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(new JSONObject()
                .set("errCode", 401)
                .set("errMsg", message)
                .toString());
    }

    /**
     * 处理无权限的请求（HTTP 403）
     */
    private void handleForbidden(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(new JSONObject()
                .set("errCode", 403)
                .set("errMsg", message)
                .toString());
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) throws Exception {
        // 不需要处理
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // 不需要处理
    }
}