package com.rednote.rednoteauth.exception;

import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.rednote.rednoteauth.controller.TestController;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 仅加载 MVC 层，验证真实请求的异常转换，无需连接数据库。 */
@WebMvcTest
@ContextConfiguration(classes = {GlobalExceptionHandler.class, TestController.class,
        GlobalExceptionHandlerTests.ExceptionTestController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preservesBusinessErrorCodeAndMessage() throws Exception {
        mockMvc.perform(get("/exception-test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_20001"))
                .andExpect(jsonPath("$.message").value("验证码错误"));
    }

    @Test
    void acceptsServiceErrorEnum() throws Exception {
        mockMvc.perform(get("/exception-test/enum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("10001"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void hidesInternalExceptionDetails() throws Exception {
        mockMvc.perform(get("/exception-test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("10000"))
                .andExpect(jsonPath("$.message").value("系统异常，请稍后重试"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handlesMissingRequestParameter() throws Exception {
        mockMvc.perform(get("/exception-test/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("10001"));
    }

    @Test
    void handlesParameterTypeMismatch() throws Exception {
        mockMvc.perform(get("/exception-test/parameter").param("id", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("10001"));
    }

    @Test
    void handlesMalformedJson() throws Exception {
        mockMvc.perform(post("/exception-test/body")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("10001"));
    }

    @Test
    void preservesMethodNotAllowedStatusAndHeader() throws Exception {
        mockMvc.perform(post("/exception-test/parameter"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void successfulEndpointStillReturnsOriginalResponse() throws Exception {
        mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("this is rednote!"));
    }

    /** 异常触发接口仅存在于测试代码中。 */
    @RestController
    static class ExceptionTestController {

        @GetMapping("/exception-test/business")
        public Response<Void> business() {
            throw new BizException("AUTH_20001", "验证码错误");
        }

        @GetMapping("/exception-test/enum")
        public Response<Void> enumError() {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        @GetMapping("/exception-test/unexpected")
        public Response<Void> unexpected() {
            throw new IllegalStateException("内部数据库连接信息");
        }

        @GetMapping("/exception-test/parameter")
        public Response<Integer> parameter(@RequestParam("id") Integer id) {
            return Response.success(id);
        }

        @PostMapping("/exception-test/body")
        public Response<Payload> body(@RequestBody Payload payload) {
            return Response.success(payload);
        }
    }

    record Payload(String name) {
    }
}
