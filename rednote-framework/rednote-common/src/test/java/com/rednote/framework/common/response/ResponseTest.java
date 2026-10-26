package com.rednote.framework.common.response;

import com.rednote.framework.common.enums.ResponseCodeEnum;
import com.rednote.framework.common.exception.BaseExceptionInterface;
import com.rednote.framework.common.exception.BizException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.Assert.*;

public class ResponseTest {

    @Test
    public void successWithoutDataHasNoError() {
        Response<Void> response = Response.success();

        assertTrue(response.isSuccess());
        assertNull(response.getData());
        assertNull(response.getErrorCode());
        assertNull(response.getMessage());
    }

    @Test
    public void successPreservesTypedPayload() {
        List<String> payload = List.of("rednote", "common");
        Response<List<String>> response = Response.success(payload);

        assertTrue(response.isSuccess());
        assertSame(payload, response.getData());
        assertNull(response.getErrorCode());
    }

    @Test
    public void defaultFailureUsesSystemError() {
        Response<Void> response = Response.fail();

        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(), response.getErrorCode());
        assertEquals(ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    public void failureAcceptsCustomMessageAndCode() {
        Response<Void> messageOnly = Response.fail("服务暂不可用");
        assertFalse(messageOnly.isSuccess());
        assertEquals(ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(), messageOnly.getErrorCode());
        assertEquals("服务暂不可用", messageOnly.getMessage());

        Response<Void> custom = Response.fail("AUTH_001", "登录已过期");
        assertFalse(custom.isSuccess());
        assertEquals("AUTH_001", custom.getErrorCode());
        assertEquals("登录已过期", custom.getMessage());
        assertNull(custom.getData());
    }

    @Test
    public void businessExceptionPreservesCodeMessageAndCause() {
        Throwable cause = new IllegalArgumentException("internal detail");
        BizException exception = new BizException(ResponseCodeEnum.PARAM_NOT_VALID, cause);

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), exception.getErrorCode());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorMessage(), exception.getMessage());
        assertEquals(exception.getMessage(), exception.getErrorMessage());
        assertSame(cause, exception.getCause());

        Response<Void> response = Response.fail(exception);
        assertFalse(response.isSuccess());
        assertEquals(exception.getErrorCode(), response.getErrorCode());
        assertEquals(exception.getErrorMessage(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    public void businessModulesCanProvideTheirOwnErrorCodes() {
        BaseExceptionInterface error = new BaseExceptionInterface() {
            @Override
            public String getErrorCode() {
                return "AUTH_002";
            }

            @Override
            public String getErrorMessage() {
                return "账号不存在";
            }
        };
        BizException exception = new BizException(error);
        Response<Void> response = Response.fail(error);

        assertEquals("AUTH_002", exception.getErrorCode());
        assertEquals("账号不存在", exception.getMessage());
        assertEquals(exception.getErrorCode(), response.getErrorCode());
        assertEquals(exception.getMessage(), response.getMessage());
        assertFalse(response.isSuccess());
    }

    @Test
    public void customBusinessExceptionCanBeConvertedToResponse() {
        BizException exception = new BizException("AUTH_003", "账号已被禁用");
        Response<Void> response = Response.fail(exception);

        assertFalse(response.isSuccess());
        assertEquals("AUTH_003", response.getErrorCode());
        assertEquals("账号已被禁用", response.getMessage());
    }

    @Test
    public void responseWithSerializablePayloadCanBeSerialized() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(Response.success("rednote"));
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            Response<?> restored = (Response<?>) input.readObject();
            assertTrue(restored.isSuccess());
            assertEquals("rednote", restored.getData());
        }
    }
}
