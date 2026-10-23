package com.rednote.rednoteauth.sms;

import com.aliyun.dypnsapi20170525.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 使用虚构凭据验证配置和注入，不发送短信。 */
class AliyunSmsClientConfigTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AliyunSmsClientConfig.class);

    @Test
    void bindsCredentialsAndRegistersClientAndHelper() {
        runner.withPropertyValues("sms.aliyun.enabled=true",
                "sms.aliyun.access-key-id=test-access-key-id",
                "sms.aliyun.access-key-secret=test-access-key-secret",
                "sms.aliyun.sign-name=test-sign",
                "sms.aliyun.template-code=100001").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Client.class)
                            .hasSingleBean(AliyunSmsHelper.class).hasSingleBean(SmsSender.class);
                    AliyunAccessKeyProperties keys = context.getBean(AliyunAccessKeyProperties.class);
                    assertThat(keys.getAccessKeyId()).isEqualTo("test-access-key-id");
                    assertThat(keys.getAccessKeySecret()).isEqualTo("test-access-key-secret");
                    assertThat(context.getBean("aliyunSmsClient")).isSameAs(context.getBean(Client.class));
                    assertThat(context.getBean(SmsSender.class)).isSameAs(context.getBean(AliyunSmsHelper.class));
                });
    }

    @Test
    void disabledModeStartsWithoutCredentialsOrCloudClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(Client.class)
                    .doesNotHaveBean(AliyunSmsHelper.class).hasSingleBean(SmsSender.class);
            assertThrows(RuntimeException.class,
                    () -> context.getBean(SmsSender.class).sendVerificationCode("18019939108", "012345"));
        });
    }

    @Test
    void enabledModeRejectsMissingSecret() {
        runner.withPropertyValues("sms.aliyun.enabled=true", "sms.aliyun.access-key-id=test-id")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledModeRejectsMissingTemplate() {
        runner.withPropertyValues("sms.aliyun.enabled=true",
                        "sms.aliyun.access-key-id=test-id", "sms.aliyun.access-key-secret=test-secret",
                        "sms.aliyun.sign-name=test-sign")
                .run(context -> assertThat(context).hasFailed());
    }
}
