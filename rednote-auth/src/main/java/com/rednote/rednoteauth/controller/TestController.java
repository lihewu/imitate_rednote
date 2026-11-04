package com.rednote.rednoteauth.controller;

import com.rednote.framework.common.response.Response;
import com.rednote.rednoteauth.alarm.AlarmInterface;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用于验证 Nacos 动态切换告警方式。 */
@RestController
public class TestController {
    private final AlarmInterface alarmHelper;

    public TestController(AlarmInterface alarmHelper) {
        this.alarmHelper = alarmHelper;
    }

    @GetMapping("/alarm")
    public Response<Void> alarm() {
        alarmHelper.send("系统异常测试，请开发同学及时排查处理。");
        return Response.success();
    }
}
