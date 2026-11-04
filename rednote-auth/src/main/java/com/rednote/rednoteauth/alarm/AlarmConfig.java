package com.rednote.rednoteauth.alarm;

import com.rednote.rednoteauth.alarm.impl.MailAlarmHelper;
import com.rednote.rednoteauth.alarm.impl.SmsAlarmHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration(proxyBeanMethods = false)
public class AlarmConfig {
    private static final Logger log = LoggerFactory.getLogger(AlarmConfig.class);

    // Nacos 刷新后销毁旧实例，下次调用代理时按最新配置重新选择实现。
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.INTERFACES)
    public AlarmInterface alarmHelper(@Value("${alarm.type:sms}") String type) {
        AlarmInterface helper = switch (type) {
            case "sms" -> new SmsAlarmHelper();
            case "mail" -> new MailAlarmHelper();
            default -> throw new IllegalArgumentException("alarm.type 仅支持 sms 或 mail，当前值：" + type);
        };
        log.info("告警方式已加载：{}", type);
        return helper;
    }
}
