package com.rednote.rednoteauth.model.vo.user;

import lombok.Data;
import lombok.ToString;

@Data
public class UserLoginReqVO {
    private String phone;

    @ToString.Exclude
    private String code;

    @ToString.Exclude
    private String password;

    private Integer type;
}
