package com.rednote.rednoteauth.service;

import com.quanxiaoha.framework.common.exception.BizException;
import com.rednote.rednoteauth.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private final VerificationCodeService service = new VerificationCodeService(redis, sender);
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
        verify(sender).sendVerificationCode(PHONE, code.getValue());
    }

    @Test
    void duplicateRequestDoesNotSendSmsOrOverwriteCode() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(false);
        BizException error = assertThrows(BizException.class, () -> service.send(PHONE));
        assertEquals("AUTH-20000", error.getErrorCode());
        assertEquals("请求太频繁，请3分钟后再试", error.getMessage());
        verifyNoInteractions(sender);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void failedDeliveryRemovesOnlyOwnCodeAndReturnsBusinessError() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("provider unavailable")).when(sender)
                .sendVerificationCode(eq(PHONE), anyString());
        BizException error = assertThrows(BizException.class, () -> service.send(PHONE));
        assertEquals("AUTH-20001", error.getErrorCode());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).sendVerificationCode(eq(PHONE), code.capture());
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq(code.getValue()));
    }

    @Test
    void redisFailureDoesNotSendSms() {
        when(values.setIfAbsent(eq(KEY), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(IllegalStateException.class, () -> service.send(PHONE));
        verifyNoInteractions(sender);
    }
}
