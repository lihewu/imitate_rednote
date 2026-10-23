package com.rednote.rednoteauth.controller;

import com.quanxiaoha.framework.common.exception.BizException;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import com.rednote.rednoteauth.exception.GlobalExceptionHandler;
import com.rednote.rednoteauth.service.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@ContextConfiguration(classes = {VerificationCodeController.class, GlobalExceptionHandler.class})
class VerificationCodeControllerTests {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private VerificationCodeService service;

    @Test
    void acceptsPhoneAsJsonWithoutReturningCode() throws Exception {
        mvc.perform(post("/verification/code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"18019939108\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        verify(service).send("18019939108");
    }

    @Test
    void returnsSpecifiedFrequencyError() throws Exception {
        doThrow(new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY))
                .when(service).send("18019939108");
        mvc.perform(post("/verification/code/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"18019939108\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求太频繁，请3分钟后再试"))
                .andExpect(jsonPath("$.errorCode").value("AUTH-20000"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsMissingBlankAndInvalidPhoneBeforeCallingService() throws Exception {
        for (String body : new String[]{"{}", "{\"phone\":null}", "{\"phone\":\"\"}",
                "{\"phone\":\"123\"}", "{\"phone\":\"1801993910x\"}", "{"}) {
            mvc.perform(post("/verification/code/send").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(service);
    }
}
