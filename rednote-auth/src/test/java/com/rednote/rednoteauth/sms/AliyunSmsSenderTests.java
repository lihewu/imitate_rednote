package com.rednote.rednoteauth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.rednote.rednoteauth.config.SmsConfig;
import com.rednote.rednoteauth.config.SmsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AliyunSmsSenderTests {
    private final Client client = mock(Client.class);
    private final SmsProperties properties = new SmsProperties();
    private final AliyunSmsSender sender = new AliyunSmsSender(client, properties);

    @Test
    void sendsConfiguredTemplateAndPreservesLeadingZero() throws Exception {
        properties.setSignName("测试签名");
        properties.setTemplateCode("SMS_TEST");
        when(client.sendSms(any())).thenReturn(new SendSmsResponse()
                .setBody(new SendSmsResponseBody().setCode("OK")));
        sender.sendVerificationCode("18019939108", "012345");
        ArgumentCaptor<SendSmsRequest> request = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSms(request.capture());
        assertEquals("18019939108", request.getValue().getPhoneNumbers());
        assertEquals("测试签名", request.getValue().getSignName());
        assertEquals("SMS_TEST", request.getValue().getTemplateCode());
        assertEquals("{\"code\":\"012345\"}", request.getValue().getTemplateParam());
    }

    @Test
    void rejectsProviderBusinessFailure() throws Exception {
        when(client.sendSms(any())).thenReturn(new SendSmsResponse()
                .setBody(new SendSmsResponseBody().setCode("isv.BUSINESS_LIMIT_CONTROL")));
        assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("18019939108", "012345"));
    }

    @Test
    void rejectsTransportFailure() throws Exception {
        when(client.sendSms(any())).thenThrow(new Exception("timeout"));
        assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("18019939108", "012345"));
    }

    @Test
    void disabledServiceNeverPretendsToSend() throws Exception {
        SmsSender disabled = new SmsConfig().smsSender(new SmsProperties());
        assertThrows(IllegalStateException.class,
                () -> disabled.sendVerificationCode("18019939108", "012345"));
    }
}
