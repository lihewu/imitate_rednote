package com.rednote.rednoteauth.config;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import com.rednote.rednoteauth.sms.AliyunSmsSender;
import com.rednote.rednoteauth.sms.SmsSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {
    @Bean
    public SmsSender smsSender(SmsProperties properties) throws Exception {
        if (!properties.isEnabled()) {
            return (phone, code) -> {
                throw new IllegalStateException("短信服务未启用");
            };
        }
        Assert.hasText(properties.getAccessKeyId(), "必须配置短信 AccessKey ID");
        Assert.hasText(properties.getAccessKeySecret(), "必须配置短信 AccessKey Secret");
        Assert.hasText(properties.getSignName(), "必须配置短信签名");
        Assert.hasText(properties.getTemplateCode(), "必须配置短信模板");
        Config config = new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint("dysmsapi.aliyuncs.com")
                .setConnectTimeout(3000)
                .setReadTimeout(5000);
        return new AliyunSmsSender(new Client(config), properties);
    }
}
