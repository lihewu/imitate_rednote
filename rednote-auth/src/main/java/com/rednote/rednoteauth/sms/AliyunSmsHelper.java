package com.rednote.rednoteauth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyun.tea.TeaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 号码认证短信：发送本地生成的验证码，后续由 Redis 校验。 */
public class AliyunSmsHelper implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsHelper.class);
    private final Client client;
    private final SmsProperties properties;

    public AliyunSmsHelper(Client client, SmsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("验证码必须为六位数字");
        }
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setCountryCode("86")
                .setSignName(properties.getSignName())
                .setTemplateCode(properties.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\",\"min\":\"3\"}")
                .setCodeLength(6L)
                .setValidTime(180L)
                .setInterval(180L)
                .setReturnVerifyCode(false);
        try {
            String maskedPhone = phone.length() == 11
                    ? phone.substring(0, 3) + "****" + phone.substring(7) : "***";
            log.info("开始号码认证短信发送，phone={}", maskedPhone);
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCodeWithOptions(request,
                    new RuntimeOptions().setAutoretry(false).setConnectTimeout(3000).setReadTimeout(5000));
            if (response == null || response.getBody() == null
                    || !"OK".equals(response.getBody().getCode())
                    || !Boolean.TRUE.equals(response.getBody().getSuccess())) {
                log.warn("号码认证拒绝请求，phone={}, code={}, requestId={}", maskedPhone,
                        response == null || response.getBody() == null ? "EMPTY_RESPONSE" : response.getBody().getCode(),
                        response == null || response.getBody() == null ? null : response.getBody().getRequestId());
                throw new IllegalStateException("短信供应商未接受发送请求");
            }
            log.info("号码认证已受理短信，phone={}, requestId={}，最终送达请查看运营商回执",
                    maskedPhone, response.getBody().getRequestId());
        } catch (TeaException exception) {
            log.warn("号码认证 API 错误，code={}, status={}, requestId={}", exception.getCode(),
                    exception.getStatusCode(), exception.getData() == null ? null : exception.getData().get("RequestId"));
            throw new IllegalStateException("短信发送失败", exception);
        } catch (Exception exception) {
            log.warn("号码认证调用失败，errorType={}", exception.getClass().getSimpleName());
            throw new IllegalStateException("短信发送失败", exception);
        }
    }
}
