package com.qj.services.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解，用于在方法上标记所需的功能权限
 * 可以在Controller方法上使用该注解进行权限控制
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    /**
     * 功能码
     * 
     * @return 执行该方法所需的功能码
     */
    int value();

    /**
     * 是否严格验证（默认严格验证）
     * 严格验证时，若用户无权限会抛出异常
     * 非严格验证时，无权限会返回false但不抛异常
     * 
     * @return 是否严格验证
     */
    boolean strict() default true;
}