package com.rednote.framework.common.exception;

/**
 * 统一错误码约定。业务模块的错误码枚举也可以实现此接口。
 */
public interface BaseExceptionInterface {

    /** 返回用于识别错误的业务码。 */
    String getErrorCode();

    /** 返回可向调用方展示的错误信息。 */
    String getErrorMessage();
}
