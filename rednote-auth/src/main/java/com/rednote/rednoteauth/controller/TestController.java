package com.rednote.rednoteauth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.rednote.framework.biz.operationlog.aspect.ApiOperationLog;
import com.rednote.framework.common.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 测试统一响应格式及 Sa-Token 登录认证的接口。 */
@RestController
public class TestController {

    @ApiOperationLog(description = "测试接口")
    @GetMapping("/test")
    public Response<String> test() {
        return Response.success("this is rednote!");
    }

    /** 演示账号登录；正式登录时需替换为真实用户查询和密码校验。 */
    @PostMapping("/login")
    public Response<SaTokenInfo> login(@RequestParam("username") String username,
                                       @RequestParam("password") String password) {
        if (!"zhang".equals(username) || !"123456".equals(password)) {
            return Response.fail("用户名或密码错误");
        }
        StpUtil.login(10001L);
        return Response.success(StpUtil.getTokenInfo());
    }

    /** 携带登录返回的 satoken 请求头或 Cookie 查询登录状态。 */
    @GetMapping("/isLogin")
    public Response<Boolean> isLogin() {
        return Response.success(StpUtil.isLogin());
    }
}
