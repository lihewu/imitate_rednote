package com.rednote.rednoteauth.model.vo.verificationcode;

import com.rednote.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发送短信验证码的请求参数。 */
@Data
public class SendVerificationCodeReqVO {

    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;
}
