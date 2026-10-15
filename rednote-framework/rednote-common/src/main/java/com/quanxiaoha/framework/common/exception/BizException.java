package com.quanxiaoha.framework.common.exception;

import java.util.Objects;

/**
 * 业务异常，携带业务错误码和对外提示，可选择保留内部异常原因。
 */
public class BizException extends RuntimeException implements BaseExceptionInterface {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public BizException(BaseExceptionInterface exception) {
        this(exception, null);
    }

    public BizException(BaseExceptionInterface exception, Throwable cause) {
        this(Objects.requireNonNull(exception, "exception").getErrorCode(),
                exception.getErrorMessage(), cause);
    }

    public BizException(String errorCode, String errorMessage) {
        this(errorCode, errorMessage, null);
    }

    public BizException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMessage() {
        return getMessage();
    }
}
