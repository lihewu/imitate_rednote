package com.quanxiaoha.framework.common.response;

import com.quanxiaoha.framework.common.enums.ResponseCodeEnum;
import com.quanxiaoha.framework.common.exception.BaseExceptionInterface;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一接口响应。业务模块可通过泛型指定响应数据类型。
 *
 * @param <T> 响应数据类型
 */
@Data
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求是否成功。 */
    private boolean success = true;

    /** 失败时的错误码，成功时为空。 */
    private String errorCode;

    /** 返回给调用方的提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /** 返回不携带数据的成功响应。 */
    public static <T> Response<T> success() {
        return new Response<>();
    }

    /** 返回携带业务数据的成功响应。 */
    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setData(data);
        return response;
    }

    /** 使用通用系统错误返回失败响应。 */
    public static <T> Response<T> fail() {
        return fail(ResponseCodeEnum.SYSTEM_ERROR);
    }

    /** 使用通用系统错误码和自定义提示返回失败响应。 */
    public static <T> Response<T> fail(String message) {
        return fail(ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(), message);
    }

    /** 使用自定义错误码和提示返回失败响应。 */
    public static <T> Response<T> fail(String errorCode, String message) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        return response;
    }

    /** 将错误码枚举或业务异常转换为响应，不暴露异常堆栈及内部原因。 */
    public static <T> Response<T> fail(BaseExceptionInterface exception) {
        Objects.requireNonNull(exception, "exception");
        return fail(exception.getErrorCode(), exception.getErrorMessage());
    }
}
