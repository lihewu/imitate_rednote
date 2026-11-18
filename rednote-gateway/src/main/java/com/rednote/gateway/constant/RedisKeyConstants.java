package com.rednote.gateway.constant;

/**
 * 网关读取鉴权缓存使用的 Redis Key 常量，需与认证服务保持一致。
 */
public final class RedisKeyConstants {

    /** 用户角色集合 Key 前缀（拼接用户主键 ID，值为角色编码 JSON 数组）。 */
    public static final String USER_ROLES_KEY_PREFIX = "user:roles:";

    /** Hash：角色编码 -> 权限编码 JSON 数组。 */
    public static final String ROLE_PERMISSIONS_KEY = "role:permissions";

    private RedisKeyConstants() {
    }

    /** 构建指定用户的角色缓存 Key。 */
    public static String buildUserRolesKey(Object userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }
}
