package com.rednote.rednoteauth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.slf4j.LoggerFactory;
import com.rednote.framework.common.exception.BizException;

/** 创建号码认证客户端及短信工具，统一交由 Spring 管理。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AliyunAccessKeyProperties.class, SmsProperties.class})
public class AliyunSmsClientConfig {

    @Bean("aliyunSmsClient")
    @ConditionalOnProperty(prefix = "sms.aliyun", name = "enabled", havingValue = "true")
    public Client aliyunSmsClient(AliyunAccessKeyProperties accessKey) throws Exception {
        Assert.hasText(accessKey.getAccessKeyId(), "必须配置短信 AccessKey ID");
        Assert.hasText(accessKey.getAccessKeySecret(), "必须配置短信 AccessKey Secret");
        Config config = new Config()
                .setAccessKeyId(accessKey.getAccessKeyId())
                .setAccessKeySecret(accessKey.getAccessKeySecret())
                .setEndpoint("dypnsapi.aliyuncs.com")
                .setConnectTimeout(3000)
                .setReadTimeout(5000);
        return new Client(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "sms.aliyun", name = "enabled", havingValue = "true")
    public AliyunSmsHelper aliyunSmsHelper(@Qualifier("aliyunSmsClient") Client client,
                                         SmsProperties properties) {
        Assert.hasText(properties.getSignName(), "必须配置短信签名");
        Assert.hasText(properties.getTemplateCode(), "必须配置短信模板");
        LoggerFactory.getLogger(AliyunSmsClientConfig.class).info("号码认证短信已启用，endpoint=dypnsapi.aliyuncs.com");
        return new AliyunSmsHelper(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "sms.aliyun", name = "enabled", havingValue = "false", matchIfMissing = true)
    public SmsSender disabledSmsSender() {
        LoggerFactory.getLogger(AliyunSmsClientConfig.class).warn("短信服务未启用，请检查当前运行配置的 ALIYUN_SMS_ENABLED");
        return new SmsSender() {
            @Override
            public void checkReady() {
                throw new BizException("AUTH-20001", "短信服务未启用，请检查运行配置");
            }

            @Override
            public void sendVerificationCode(String phone, String code) {
                checkReady();
            }
        };
    }
}
