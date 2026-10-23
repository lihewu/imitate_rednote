package com.rednote.rednoteauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 短信调用专用线程池，由 Spring 管理初始化和关闭。 */
@Configuration(proxyBeanMethods = false)
public class ThreadPoolConfig {

    @Bean("smsTaskExecutor")
    public ThreadPoolTaskExecutor smsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sms-send-");
        // 拒绝时交给业务层处理，避免在 HTTP 请求线程中执行慢速短信调用。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
