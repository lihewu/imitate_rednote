package com.rednote.rednoteauth.enums;

import com.rednote.framework.common.exception.BaseExceptionInterface;

/** 认证服务错误码，通用错误沿用公共模块的定义。 */
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR(com.rednote.framework.common.enums.ResponseCodeEnum.SYSTEM_ERROR),
    PARAM_NOT_VALID(com.rednote.framework.common.enums.ResponseCodeEnum.PARAM_NOT_VALID),
    VERIFICATION_CODE_SEND_FREQUENTLY("AUTH-20000", "请求太频繁，请3分钟后再试"),
    VERIFICATION_CODE_SEND_FAILED("AUTH-20001", "短信发送失败，请稍后重试"),
    VERIFICATION_CODE_ERROR("AUTH-20002", "验证码错误或已过期"),
    PHONE_OR_PASSWORD_ERROR("AUTH-20003", "手机号或密码错误"),
    USER_DISABLED("AUTH-20004", "账号不可用");

    private final String errorCode;
    private final String errorMessage;

    ResponseCodeEnum(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    ResponseCodeEnum(BaseExceptionInterface exception) {
        this.errorCode = exception.getErrorCode();
        this.errorMessage = exception.getErrorMessage();
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
}
