package com.rednote.rednoteauth.controller;

import com.rednote.framework.common.response.Response;
import com.rednote.rednoteauth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.rednote.rednoteauth.service.VerificationCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verification/code")
public class VerificationCodeController {
    private final VerificationCodeService verificationCodeService;

    public VerificationCodeController(VerificationCodeService verificationCodeService) {
        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/send")
    public Response<Void> send(@Valid @RequestBody SendVerificationCodeReqVO request) {
        verificationCodeService.send(request.getPhone());
        return Response.success();
    }
}
