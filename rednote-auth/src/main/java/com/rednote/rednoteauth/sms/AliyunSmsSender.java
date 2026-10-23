package com.rednote.rednoteauth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.rednote.rednoteauth.config.SmsProperties;

/** 模板使用 ${code} 变量；不输出手机号、验证码或密钥。 */
public class AliyunSmsSender implements SmsSender {
    private final Client client;
    private final SmsProperties properties;

    public AliyunSmsSender(Client client, SmsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("验证码必须为六位数字");
        }
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(properties.getSignName())
                .setTemplateCode(properties.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        try {
            SendSmsResponse response = client.sendSms(request);
            if (response == null || response.getBody() == null
                    || !"OK".equals(response.getBody().getCode())) {
                throw new IllegalStateException("短信供应商未接受发送请求");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("短信发送失败", exception);
        }
    }
}
