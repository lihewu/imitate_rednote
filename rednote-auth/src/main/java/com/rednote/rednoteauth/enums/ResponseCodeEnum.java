package com.rednote.rednoteauth.enums;

import com.quanxiaoha.framework.common.exception.BaseExceptionInterface;

/** 认证服务错误码，通用错误沿用公共模块的定义。 */
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR(com.quanxiaoha.framework.common.enums.ResponseCodeEnum.SYSTEM_ERROR),
    PARAM_NOT_VALID(com.quanxiaoha.framework.common.enums.ResponseCodeEnum.PARAM_NOT_VALID);

    private final String errorCode;
    private final String errorMessage;

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
