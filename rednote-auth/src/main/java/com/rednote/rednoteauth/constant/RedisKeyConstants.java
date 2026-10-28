package com.rednote.rednoteauth.constant;

/** 认证服务 Redis Key 统一定义。 */
public final class RedisKeyConstants {

    public static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    public static final String REDNOTE_ID_GENERATOR_KEY = "rednote_id_generator";

    public static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /** Hash：角色编码 -> 权限编码 JSON 数组。 */
    public static final String ROLE_PERMISSIONS_KEY = "role:permissions";

    public static String buildRolePermissionsLockKey(String cacheKey) {
        // 与缓存使用同一 Redis Cluster hash slot；也兼容自定义 Key 自带 hash tag。
        int start = cacheKey.indexOf('{');
        int end = start < 0 ? -1 : cacheKey.indexOf('}', start + 1);
        String tag = end > start + 1 ? cacheKey.substring(start + 1, end) : cacheKey;
        return "{" + tag + "}:sync:lock";
    }

    private RedisKeyConstants() {
    }

    /** 构建指定手机号的验证码缓存 Key。 */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }

    /** 角色缓存使用数据库用户主键，而非展示用的 rednote_id。 */
    public static String buildUserRolesKey(Long userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }
}
