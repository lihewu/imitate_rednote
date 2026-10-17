package com.quanxiaoha.framework.biz.operationlog.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录操作日志的 Spring Bean 方法。
 * 通过 Spring 代理调用时生效。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiOperationLog {

    /** 业务操作描述，如“用户登录”。 */
    String description() default "";
}