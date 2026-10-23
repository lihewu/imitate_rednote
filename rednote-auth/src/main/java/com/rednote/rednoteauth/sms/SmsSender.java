package com.rednote.rednoteauth.sms;

/** 短信供应商适配接口。仅在供应商确认接受发送请求后正常返回，失败则抛出异常。 */
public interface SmsSender {
    /** 在提交后台任务前检查短信服务是否可用。 */
    default void checkReady() {
    }

    void sendVerificationCode(String phone, String code);
}
