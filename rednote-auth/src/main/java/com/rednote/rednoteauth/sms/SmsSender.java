package com.rednote.rednoteauth.sms;

/** 短信供应商适配接口。仅在供应商确认接受发送请求后正常返回，失败则抛出异常。 */
public interface SmsSender {
    void sendVerificationCode(String phone, String code);
}
