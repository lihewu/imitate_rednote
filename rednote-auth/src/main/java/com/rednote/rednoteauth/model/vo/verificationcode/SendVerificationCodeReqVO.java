package com.rednote.rednoteauth.model.vo.verificationcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 发送短信验证码的请求参数。 */
@Data
public class SendVerificationCodeReqVO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "手机号格式不正确")
    private String phone;
}
