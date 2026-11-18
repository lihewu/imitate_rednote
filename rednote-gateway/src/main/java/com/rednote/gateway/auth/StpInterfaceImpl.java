package com.rednote.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rednote.gateway.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sa-Token 自定义权限数据源：从 Redis 读取当前用户的角色与权限集合。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> userRoles = getRoleList(loginId, loginType);
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> rolePermissionsRaw = stringRedisTemplate.opsForHash()
                .multiGet(RedisKeyConstants.ROLE_PERMISSIONS_KEY, new ArrayList<>(userRoles));
        if (rolePermissionsRaw == null || rolePermissionsRaw.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> permissions = new LinkedHashSet<>();
        for (Object raw : rolePermissionsRaw) {
            if (raw == null) {
                continue;
            }
            String json = raw.toString();
            if (StringUtils.isBlank(json)) {
                continue;
            }
            List<String> rolePermissions = parseStringList(json, "role permissions");
            if (rolePermissions != null) {
                for (String permission : rolePermissions) {
                    if (StringUtils.isNotBlank(permission)) {
                        permissions.add(permission);
                    }
                }
            }
        }
        return new ArrayList<>(permissions);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        String userRolesKey = RedisKeyConstants.buildUserRolesKey(loginId);
        String userRolesJson = stringRedisTemplate.opsForValue().get(userRolesKey);
        if (StringUtils.isBlank(userRolesJson)) {
            return Collections.emptyList();
        }
        List<String> roles = parseStringList(userRolesJson, "user roles");
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(roles.size());
        for (String role : roles) {
            if (StringUtils.isNotBlank(role)) {
                result.add(role);
            }
        }
        return result;
    }

    private List<String> parseStringList(String json, String target) {
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse {} JSON from Redis: {}", target, json, e);
            throw new IllegalStateException("解析 Redis 中的 " + target + " 失败", e);
        }
    }
}
