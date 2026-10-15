package com.rednote.rednoteauth.controller;

import com.quanxiaoha.framework.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 测试统一响应格式的接口。 */
@RestController
public class TestController {

    @GetMapping("/test")
    public Response<String> test() {
        return Response.success("this is rednote!");
    }
}
