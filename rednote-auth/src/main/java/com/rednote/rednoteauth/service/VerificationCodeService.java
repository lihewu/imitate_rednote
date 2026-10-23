package com.rednote.rednoteauth.service;

/** 短信验证码业务接口。 */
public interface VerificationCodeService {

    /** 提交异步发送任务；有效期内重复请求或提交失败时抛出业务异常。 */
    void send(String phone);
}
