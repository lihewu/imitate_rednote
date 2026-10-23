package com.rednote.rednoteauth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 不生成 toString，避免密钥意外出现在日志中。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sms.aliyun")
public class SmsProperties {
    private boolean enabled;
    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String templateCode;
}
