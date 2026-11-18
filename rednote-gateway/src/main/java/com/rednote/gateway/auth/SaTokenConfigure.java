package com.rednote.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 网关全局鉴权配置。
 */
@Slf4j
@Configuration
public class SaTokenConfigure {

    /**
     * 注册 Sa-Token 全局过滤器，拦截所有路由并进行登录与权限校验。
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 鉴权规则：每次请求进入时执行
                .setAuth(obj -> {
                    log.debug("==> SaReactorFilter intercept path: {}", SaHolder.getRequest().getRequestPath());
                    // 登录校验：放行登录与发送验证码接口，其余接口均需登录
                    SaRouter.match("/**")
                            .notMatch("/auth/user/login")
                            .notMatch("/auth/verification/code/send")
                            .check(r -> StpUtil.checkLogin());
                })
                // 异常处理：每次 setAuth 抛出异常时进入（后续 7.5 将替换为网关全局异常处理）
                .setError(e -> {
                    log.warn("==> SaReactorFilter auth error: {}", e.getMessage());
                    return SaResult.error(e.getMessage());
                });
    }
}
