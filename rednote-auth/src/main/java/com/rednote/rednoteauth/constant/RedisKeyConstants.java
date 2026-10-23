package com.rednote.rednoteauth.constant;

/** 认证服务 Redis Key 统一定义。 */
public final class RedisKeyConstants {

    public static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    private RedisKeyConstants() {
    }

    /** 构建指定手机号的验证码缓存 Key。 */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }
}
