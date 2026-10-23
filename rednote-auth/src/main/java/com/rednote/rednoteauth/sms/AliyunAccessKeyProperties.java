package com.rednote.rednoteauth.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 AccessKey；不生成包含密钥的 toString。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sms.aliyun")
public class AliyunAccessKeyProperties {
    private String accessKeyId;
    private String accessKeySecret;
}
