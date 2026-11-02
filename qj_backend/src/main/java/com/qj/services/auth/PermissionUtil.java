package com.qj.services.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限工具类
 * 提供在服务层中进行权限检查的工具方法
 */
public class PermissionUtil {

    /**
     * 检查当前用户是否有指定功能的权限
     * 
     * @param functionCode 功能码
     * @return 是否有权限
     */
    public static boolean hasPermission(int functionCode) {
        String username = getCurrentUsername();
        if (StrUtil.isBlank(username)) {
            return false;
        }

        JSONObject result = com.qj.services.auth.PermissionService.checkPermission(username, functionCode);
        return result.getBool("hasPermission", false);
    }

    /**
     * 检查指定用户是否有功能权限
     * 
     * @param username     用户名
     * @param functionCode 功能码
     * @return 是否有权限
     */
    public static boolean hasPermission(String username, int functionCode) {
        if (StrUtil.isBlank(username)) {
            return false;
        }

        JSONObject result = com.qj.services.auth.PermissionService.checkPermission(username, functionCode);
        return result.getBool("hasPermission", false);
    }

    /**
     * 获取当前请求的用户名
     * 
     * @return 当前用户名，如果未登录则返回null
     */
    public static String getCurrentUsername() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    // 检查session中是否存在用户信息
                    Object usernameObj = session.getAttribute("username");
                    if (usernameObj != null) {
                        return usernameObj.toString();
                    }

                    // 如果username属性不存在，尝试获取currentUser对象
                    Object userObj = session.getAttribute("currentUser");
                    if (userObj != null && userObj instanceof JSONObject) {
                        JSONObject userJson = (JSONObject) userObj;
                        return userJson.getStr("username", null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 检查权限，如果没有权限则抛出异常
     * 
     * @param functionCode 功能码
     * @throws PermissionDeniedException 如果没有权限
     */
    public static void checkPermission(int functionCode) throws PermissionDeniedException {
        if (!hasPermission(functionCode)) {
            throw new PermissionDeniedException("没有权限执行功能: " + functionCode);
        }
    }

    /**
     * 自定义权限异常
     */
    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}