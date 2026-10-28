package com.rednote.rednoteauth.service;

import com.rednote.rednoteauth.model.vo.user.UserLoginReqVO;

public interface UserService {
    /** 验证码登录时自动注册新用户，返回登录 Token。 */
    String loginAndRegister(UserLoginReqVO request);
}
