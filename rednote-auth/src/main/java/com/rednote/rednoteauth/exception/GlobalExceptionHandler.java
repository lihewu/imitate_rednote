package com.rednote.rednoteauth.exception;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 统一处理认证服务控制器抛出的业务异常、请求异常及未预期异常。 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务处理失败通过统一响应中的 success 和 errorCode 表达。 */
    @ExceptionHandler(BizException.class)
    public Response<Void> handleBizException(HttpServletRequest request, BizException exception) {
        log.warn("业务异常：uri={}, errorCode={}, message={}",
                request.getRequestURI(), exception.getErrorCode(), exception.getErrorMessage());
        return Response.fail(exception);
    }

    /** 内部错误只在服务端记录详细堆栈，对外返回统一系统提示。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleUnexpectedException(
            HttpServletRequest request, Exception exception) {
        log.error("系统异常：uri={}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.fail(ResponseCodeEnum.SYSTEM_ERROR));
    }

    /** 保留 Spring MVC 的状态码及响应头，统一参数校验、JSON 解析等异常的响应体。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseCodeEnum responseCode;
        if (statusCode.is5xxServerError()) {
            responseCode = ResponseCodeEnum.SYSTEM_ERROR;
            log.error("请求处理异常：{}", request.getDescription(false), exception);
        } else {
            responseCode = ResponseCodeEnum.PARAM_NOT_VALID;
            log.warn("请求异常：{}, type={}", request.getDescription(false),
                    exception.getClass().getSimpleName());
        }
        return super.handleExceptionInternal(exception, Response.fail(responseCode),
                headers, statusCode, request);
    }
}
