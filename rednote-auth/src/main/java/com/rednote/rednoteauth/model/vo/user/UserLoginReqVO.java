package com.rednote.rednoteauth.model.vo.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rednote.framework.common.validator.PhoneNumber;
import com.rednote.rednoteauth.enums.LoginTypeEnum;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

@Data
public class UserLoginReqVO {
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;

    @ToString.Exclude
    private String code;

    @ToString.Exclude
    private String password;

    @NotNull(message = "登录类型不能为空")
    @Min(1)
    @Max(2)
    private Integer type;

    @JsonIgnore
    @AssertTrue(message = "验证码登录需要六位数字验证码，密码登录需要非空密码（最多72字节）")
    public boolean isCredentialValid() {
        if (type == null) {
            return true;
        }
        if (type == LoginTypeEnum.VERIFICATION_CODE.getType()) {
            return code != null && code.matches("[0-9]{6}");
        }
        if (type == LoginTypeEnum.PASSWORD.getType()) {
            return password != null && !password.isBlank()
                    && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        }
        return true;
    }
}
