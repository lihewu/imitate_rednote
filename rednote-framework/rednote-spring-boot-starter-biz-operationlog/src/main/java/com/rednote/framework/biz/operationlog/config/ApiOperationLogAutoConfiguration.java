package com.rednote.framework.biz.operationlog.config;

import com.rednote.framework.biz.operationlog.aspect.ApiOperationLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 引入 starter 后自动注册业务日志切面，代理由 Spring Boot 的 AOP 自动配置启用。 */
@AutoConfiguration
public class ApiOperationLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiOperationLogAspect.class)
    public ApiOperationLogAspect apiOperationLogAspect() {
        return new ApiOperationLogAspect();
    }
}