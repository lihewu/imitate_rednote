package com.rednote.framework.common.enums;

import com.rednote.framework.common.exception.BaseExceptionInterface;

/** 通用错误码；具体业务的错误码可在各业务模块中扩展。 */
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR("10000", "系统异常，请稍后重试"),
    PARAM_NOT_VALID("10001", "请求参数不合法");

    private final String errorCode;
    private final String errorMessage;

    ResponseCodeEnum(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
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
