package com.rednote.rednoteauth.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.rednote.rednoteauth.service.impl.VerificationCodeServiceImpl;
import com.rednote.rednoteauth.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SmsSender sender = mock(SmsSender.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final VerificationCodeService service = new VerificationCodeServiceImpl(redis, sender, executor);
    private static final String PHONE = "18019939108";
    private static final String KEY = "verification_code:" + PHONE;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void storesAndSendsSameSixDigitCodeWithThreeMinuteExpiry() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(3)))).thenReturn(true);
        service.send(PHONE);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), code.capture(), eq(Duration.ofMinutes(3)));
        assertTrue(code.getValue().matches("[0-9]{6}"));
        verify(sender, never()).sendVerificationCode(anyString(), anyString());
        when(values.get(KEY)).thenReturn(code.getValue());
        runSubmittedTask();
        verify(sender).sendVerificationCode(PHONE, code.getValue());
    }

    @Test
    void duplicateRequestDoesNotSendSmsOrOverwriteCode() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(false);
        BizException error = assertThrows(BizException.class, () -> service.send(PHONE));
        assertEquals("AUTH-20000", error.getErrorCode());
        assertEquals("请求太频繁，请3分钟后再试", error.getMessage());
        verify(sender, never()).sendVerificationCode(anyString(), anyString());
        verifyNoInteractions(executor);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void failedAsyncDeliveryRemovesOnlyOwnCode() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("provider unavailable")).when(sender)
                .sendVerificationCode(eq(PHONE), anyString());
        service.send(PHONE);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), code.capture(), any(Duration.class));
        when(values.get(KEY)).thenReturn(code.getValue());
        assertDoesNotThrow(this::runSubmittedTask);
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq(code.getValue()));
    }

    @Test
    void rejectedTaskClearsReservationAndReturnsBusinessError() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));
        BizException error = assertThrows(BizException.class, () -> service.send(PHONE));
        assertEquals("AUTH-20001", error.getErrorCode());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), code.capture(), any(Duration.class));
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq(code.getValue()));
        verify(sender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void expiredQueuedTaskDoesNotSendSms() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        service.send(PHONE);
        when(values.get(KEY)).thenReturn(null);
        runSubmittedTask();
        verify(sender, never()).sendVerificationCode(anyString(), anyString());
    }

    private void runSubmittedTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        task.getValue().run();
    }

    @Test
    void disabledSmsFailsBeforeRedisOrTaskSubmission() {
        doThrow(new BizException("AUTH-20001", "短信服务未启用，请检查运行配置"))
                .when(sender).checkReady();
        BizException error = assertThrows(BizException.class, () -> service.send(PHONE));
        assertEquals("AUTH-20001", error.getErrorCode());
        verifyNoInteractions(values, executor);
    }

    @Test
    void redisFailureDoesNotSendSms() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(IllegalStateException.class, () -> service.send(PHONE));
        verify(sender, never()).sendVerificationCode(anyString(), anyString());
    }
}
