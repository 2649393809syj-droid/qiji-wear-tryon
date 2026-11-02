package com.qj.services.auth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.services.common.CRUDService;
import com.qj.consts.FunctionCodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务类
 * 负责检查用户权限
 */
public class PermissionService {
    // 用于缓存用户权限，避免频繁数据库查询
    private static final ConcurrentHashMap<String, List<String>> userPermissionsCache = new ConcurrentHashMap<>();

    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRATION_TIME = 60 * 1000; // 1分钟
    private static final ConcurrentHashMap<String, Long> userCacheTimestamp = new ConcurrentHashMap<>();

    // 数据库表名配置，可通过外部配置文件修改
    private static String userRoleTable = "role_user";
    private static String rolePermissionTable = "role_func";
    private static String functionTable = "func";

    /**
     * 配置权限系统使用的数据库表
     * 
     * @param userRoleTableName       用户角色关系表名
     * @param rolePermissionTableName 角色权限关系表名
     * @param functionTableName       功能表名
     */
    public static void configureDatabase(String userRoleTableName, String rolePermissionTableName,
            String functionTableName) {
        userRoleTable = userRoleTableName;
        rolePermissionTable = rolePermissionTableName;
        functionTable = functionTableName;
    }

    /**
     * 清除指定用户的权限缓存
     * 
     * @param username 用户名
     */
    public static void clearUserCache(String username) {
        if (StrUtil.isNotBlank(username)) {
            userPermissionsCache.remove(username);
            userCacheTimestamp.remove(username);
        }
    }

    /**
     * 清除所有用户的权限缓存
     */
    public static void clearAllCache() {
        userPermissionsCache.clear();
        userCacheTimestamp.clear();
    }

    /**
     * 检查用户是否拥有特定功能权限
     * 
     * @param username     用户名
     * @param functionCode 功能码
     * @return 检查结果
     */
    public static JSONObject checkPermission(String username, int functionCode) {
        if (StrUtil.isBlank(username)) {
            return new JSONObject()
                    .set("hasPermission", false)
                    .set("errCode", FunctionCodes.F_101_1625_ROLE_GET_PERMISSIONS)
                    .set("errMsg", "用户名不能为空");
        }

        try {
            // 检查缓存中是否有该用户的权限信息，且缓存未过期
            Long timestamp = userCacheTimestamp.get(username);
            if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRATION_TIME) {
                List<String> permissions = userPermissionsCache.get(username);
                if (permissions != null) {
                    String funcCodeStr = String.valueOf(functionCode);
                    boolean hasPermission = permissions.contains(funcCodeStr);
                    return new JSONObject()
                            .set("hasPermission", hasPermission)
                            .set("errCode", 0)
                            .set("errMsg", hasPermission ? "用户有权限执行该功能" : "用户没有权限执行该功能")
                            .set("fromCache", true);
                }
            }

            // 获取用户所有权限
            List<String> userPermissions = getUserPermissions(username);

            // 更新缓存
            userPermissionsCache.put(username, userPermissions);
            userCacheTimestamp.put(username, System.currentTimeMillis());

            // 检查是否有指定功能的权限
            String funcCodeStr = String.valueOf(functionCode);
            boolean hasPermission = userPermissions.contains(funcCodeStr);

            return new JSONObject()
                    .set("hasPermission", hasPermission)
                    .set("errCode", 0)
                    .set("errMsg", hasPermission ? "用户有权限执行该功能" : "用户没有权限执行该功能");

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject()
                    .set("hasPermission", false)
                    .set("errCode", FunctionCodes.F_101_1625_ROLE_GET_PERMISSIONS)
                    .set("errMsg", "检查权限异常: " + e.getMessage());
        }
    }

    /**
     * 获取用户的所有权限
     * 
     * @param username 用户名
     * @return 用户拥有的所有功能码列表
     */
    public static List<String> getUserPermissions(String username) {
        List<String> permissions = new ArrayList<>();

        try {
            // 1. 获取用户角色
            JSONObject userRoleParam = new JSONObject();
            userRoleParam.set("__mysql_fieldkeys__", JSONUtil.parseArray("['username']"));
            userRoleParam.set("username", username);

            JSONObject roleResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_1436_USER_GET_ROLE,
                    "F_101_1436_USER_GET_ROLE",
                    userRoleTable,
                    userRoleParam,
                    new HashSet<>());

            if (roleResult.getInt("errCode") != 0) {
                return permissions; // 返回空权限列表
            }

            JSONArray userRoles = roleResult.getJSONArray("result");
            if (userRoles == null || userRoles.isEmpty()) {
                return permissions; // 返回空权限列表
            }

            // 2. 获取角色的所有权限
            for (int i = 0; i < userRoles.size(); i++) {
                String rolename = userRoles.getJSONObject(i).getStr("rolename");
                if (StrUtil.isBlank(rolename)) {
                    continue;
                }

                JSONObject permParam = new JSONObject();
                permParam.set("__mysql_fieldkeys__", JSONUtil.parseArray("['rolename']"));
                permParam.set("rolename", rolename);

                JSONObject permResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_101_1625_ROLE_GET_PERMISSIONS,
                        "F_101_1625_ROLE_GET_PERMISSIONS",
                        rolePermissionTable,
                        permParam,
                        new HashSet<>());

                if (permResult.getInt("errCode") == 0) {
                    JSONArray rolePerms = permResult.getJSONArray("result");
                    if (rolePerms != null && !rolePerms.isEmpty()) {
                        for (int j = 0; j < rolePerms.size(); j++) {
                            String funcCode = rolePerms.getJSONObject(j).getStr("funcode");
                            if (StrUtil.isNotBlank(funcCode) && !permissions.contains(funcCode)) {
                                permissions.add(funcCode);
                            }
                        }
                    }
                }
            }

            return permissions;

        } catch (Exception e) {
            e.printStackTrace();
            return permissions; // 出现异常返回空权限列表
        }
    }

    /**
     * 检查功能码是否存在
     * 
     * @param functionCode 功能码
     * @return 检查结果
     */
    public static JSONObject checkFunctionExists(int functionCode) {
        try {
            String funcCodeStr = String.valueOf(functionCode);

            JSONObject queryParam = new JSONObject();
            queryParam.set("__mysql_fieldkeys__", JSONUtil.parseArray("['funcode']"));
            queryParam.set("funcode", funcCodeStr);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_1637_FUNCTION_CHECK_EXISTS,
                    "F_101_1637_FUNCTION_CHECK_EXISTS",
                    functionTable,
                    queryParam,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return new JSONObject()
                        .set("exists", false)
                        .set("errCode", result.getInt("errCode"))
                        .set("errMsg", "检查功能是否存在失败: " + result.getStr("errMsg"));
            }

            JSONArray functionResult = result.getJSONArray("result");
            boolean exists = functionResult != null && !functionResult.isEmpty();

            return new JSONObject()
                    .set("exists", exists)
                    .set("errCode", 0)
                    .set("errMsg", exists ? "功能存在" : "功能不存在");

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject()
                    .set("exists", false)
                    .set("errCode", FunctionCodes.F_101_1637_FUNCTION_CHECK_EXISTS)
                    .set("errMsg", "检查功能是否存在异常: " + e.getMessage());
        }
    }
}