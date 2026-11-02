package com.qj.config;


import com.qj.services.auth.PermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 权限系统配置类
 * 用于配置权限系统的数据库表名等参数
 */
@Configuration
public class PermissionConfig {

    // 从配置文件中读取表名，如果不指定则使用默认值
    @Value("${permission.user-role-table:role_user}")
    private String userRoleTable;

    @Value("${permission.role-permission-table:role_func}")
    private String rolePermissionTable;

    @Value("${permission.function-table:func}")
    private String functionTable;

    /**
     * 初始化权限系统配置
     */
    @PostConstruct
    public void init() {
        // 配置权限系统使用的数据库表
        PermissionService.configureDatabase(
                userRoleTable,
                rolePermissionTable,
                functionTable);
    }
}