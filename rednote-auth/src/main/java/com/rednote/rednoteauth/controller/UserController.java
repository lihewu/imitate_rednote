package com.rednote.rednoteauth.controller;

import com.rednote.framework.common.response.Response;
import com.rednote.rednoteauth.model.vo.user.UserLoginReqVO;
import com.rednote.rednoteauth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/login")
    public Response<String> login(@Valid @RequestBody UserLoginReqVO request) {
        return Response.success(userService.loginAndRegister(request));
    }
}
