package com.rednote.rednoteauth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AliyunSmsHelperTests {
    private final Client client = mock(Client.class);
    private final SmsProperties properties = new SmsProperties();
    private final AliyunSmsHelper sender = new AliyunSmsHelper(client, properties);

    @Test
    void sendsConfiguredTemplateAndPreservesLeadingZero() throws Exception {
        properties.setSignName("测试签名");
        properties.setTemplateCode("100001");
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        sender.sendVerificationCode("18019939108", "012345");
        ArgumentCaptor<SendSmsVerifyCodeRequest> request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), any());
        assertEquals("18019939108", request.getValue().getPhoneNumber());
        assertEquals("测试签名", request.getValue().getSignName());
        assertEquals("100001", request.getValue().getTemplateCode());
        assertEquals("{\"code\":\"012345\",\"min\":\"3\"}", request.getValue().getTemplateParam());
        assertEquals("86", request.getValue().getCountryCode());
        assertEquals(6L, request.getValue().getCodeLength());
        assertEquals(180L, request.getValue().getValidTime());
        assertEquals(180L, request.getValue().getInterval());
        assertFalse(request.getValue().getReturnVerifyCode());
    }

    @Test
    void rejectsProviderBusinessFailure() throws Exception {
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("BUSINESS_LIMIT_CONTROL").setSuccess(false)));
        assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("18019939108", "012345"));
    }

    @Test
    void rejectsIncompleteOrUnsuccessfulResponseEvenWithOkCode() throws Exception {
        for (SendSmsVerifyCodeResponse response : new SendSmsVerifyCodeResponse[]{
                null, new SendSmsVerifyCodeResponse(),
                new SendSmsVerifyCodeResponse().setBody(new SendSmsVerifyCodeResponseBody().setCode("OK")),
                new SendSmsVerifyCodeResponse().setBody(
                        new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(false))}) {
            when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(response);
            assertThrows(IllegalStateException.class,
                    () -> sender.sendVerificationCode("18019939108", "012345"));
        }
    }

    @Test
    void rejectsTransportFailure() throws Exception {
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenThrow(new Exception("timeout"));
        assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("18019939108", "012345"));
    }

    @Test
    void disabledServiceNeverPretendsToSend() throws Exception {
        SmsSender disabled = new AliyunSmsClientConfig().disabledSmsSender();
        assertThrows(com.rednote.framework.common.exception.BizException.class,
                () -> disabled.sendVerificationCode("18019939108", "012345"));
    }
}
