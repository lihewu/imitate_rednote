package com.rednote.rednoteauth;

import com.rednote.rednoteauth.constant.RedisKeyConstants;
import com.rednote.rednoteauth.domain.dataobject.RolePermissionDO;
import com.rednote.rednoteauth.domain.mapper.RolePermissionMapper;
import com.rednote.rednoteauth.service.impl.RolePermissionSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "sms.aliyun.enabled=false")
@ActiveProfiles("dev")
class RolePermissionSyncIntegrationTests {
    @Autowired RolePermissionMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    private RolePermissionDO row(String role, String permission) {
        RolePermissionDO row = new RolePermissionDO();
        row.setRoleKey(role);
        row.setPermissionKey(permission);
        return row;
    }

    @Test
    void startupPublishesDatabaseRoles() {
        for (RolePermissionDO row : mapper.selectEnabledRolePermissions()) {
            Object cached = redis.opsForHash().get(RedisKeyConstants.ROLE_PERMISSIONS_KEY, row.getRoleKey());
            assertThat(cached).isNotNull();
            if (row.getPermissionKey() != null && !row.getPermissionKey().isBlank()) {
                assertThat(cached.toString()).contains("\"" + row.getPermissionKey() + "\"");
            }
        }
    }

    @Test
    void replacesSnapshotRemovesRevokedPermissionsAndPreservesCacheOnQueryFailure() {
        String key = "rednote:test:role-permissions:" + UUID.randomUUID();
        RolePermissionMapper source = mock(RolePermissionMapper.class);
        RolePermissionSyncServiceImpl service = new RolePermissionSyncServiceImpl(source, redis, key);
        try {
            when(source.selectEnabledRolePermissions()).thenReturn(List.of(
                    row("reader", "note:read"), row("reader", "note:read"),
                    row("reader", "note:write"), row("empty", null)));
            assertThat(service.syncRolePermissions()).isEqualTo(2);
            assertThat(redis.opsForHash().get(key, "reader")).isEqualTo("[\"note:read\",\"note:write\"]");
            assertThat(redis.opsForHash().get(key, "empty")).isEqualTo("[]");
            when(source.selectEnabledRolePermissions()).thenReturn(List.of(row("reader", "note:read")));
            service.syncRolePermissions();
            assertThat(redis.opsForHash().get(key, "reader")).isEqualTo("[\"note:read\"]");
            assertThat(redis.opsForHash().hasKey(key, "empty")).isFalse();
            when(source.selectEnabledRolePermissions()).thenThrow(new IllegalStateException("database unavailable"));
            assertThatThrownBy(service::syncRolePermissions).isInstanceOf(IllegalStateException.class);
            assertThat(redis.hasKey(RedisKeyConstants.buildRolePermissionsLockKey(key))).isFalse();
            assertThat(redis.opsForHash().get(key, "reader")).isEqualTo("[\"note:read\"]");
            doReturn(List.of()).when(source).selectEnabledRolePermissions();
            assertThat(service.syncRolePermissions()).isZero();
            assertThat(redis.hasKey(key)).isFalse();
        } finally {
            redis.delete(key);
        }
    }

    @Test
    void lockExcludesOtherInstancesAndIsReleasedAfterSuccess() {
        String key = "rednote:test:role-permissions:" + UUID.randomUUID();
        String lockKey = RedisKeyConstants.buildRolePermissionsLockKey(key);
        RolePermissionMapper first = mock(RolePermissionMapper.class);
        RolePermissionMapper second = mock(RolePermissionMapper.class);
        RolePermissionSyncServiceImpl firstService = new RolePermissionSyncServiceImpl(first, redis, key);
        RolePermissionSyncServiceImpl secondService = new RolePermissionSyncServiceImpl(second, redis, key);
        when(first.selectEnabledRolePermissions()).thenAnswer(invocation -> {
            assertThat(redis.getExpire(lockKey)).isBetween(1L, 30L);
            assertThatThrownBy(secondService::syncRolePermissions)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("正在执行");
            verifyNoInteractions(second);
            assertThat(redis.hasKey(lockKey)).isTrue();
            return List.of(row("reader", "note:read"));
        });
        try {
            assertThat(firstService.syncRolePermissions()).isEqualTo(1);
            assertThat(redis.hasKey(lockKey)).isFalse();
            when(second.selectEnabledRolePermissions()).thenReturn(List.of(row("reader", "note:write")));
            assertThat(secondService.syncRolePermissions()).isEqualTo(1);
            assertThat(redis.opsForHash().get(key, "reader")).isEqualTo("[\"note:write\"]");
        } finally {
            redis.delete(List.of(key, lockKey));
        }
    }

    @Test
    void expiredOwnerCannotPublishOrReleaseNewOwnersLock() {
        String key = "rednote:test:role-permissions:" + UUID.randomUUID();
        String lockKey = RedisKeyConstants.buildRolePermissionsLockKey(key);
        RolePermissionMapper source = mock(RolePermissionMapper.class);
        RolePermissionSyncServiceImpl service = new RolePermissionSyncServiceImpl(source, redis, key);
        when(source.selectEnabledRolePermissions()).thenAnswer(invocation -> {
            // 模拟查询期间租期结束，以及另一个实例取得锁并发布新数据。
            redis.expire(lockKey, java.time.Duration.ZERO);
            assertThat(redis.opsForValue().setIfAbsent(lockKey, "new-owner", java.time.Duration.ofSeconds(30))).isTrue();
            redis.opsForHash().put(key, "reader", "[\"new:permission\"]");
            return List.of(row("reader", "stale:permission"));
        });
        try {
            assertThatThrownBy(service::syncRolePermissions).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("锁已失效");
            assertThat(redis.opsForHash().get(key, "reader")).isEqualTo("[\"new:permission\"]");
            assertThat(redis.opsForValue().get(lockKey)).isEqualTo("new-owner");
        } finally {
            redis.delete(List.of(key, lockKey));
        }
    }

    @Test
    @Transactional
    void queryExcludesDisabledDeletedAndUnlinkedPermissions() {
        String role = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("insert into t_role(role_name, role_key) values (?, ?)", "sync test", role);
        Long roleId = jdbc.queryForObject("select id from t_role where role_key = ?", Long.class, role);
        for (String suffix : List.of("enabled", "disabled", "deleted", "unlinked")) {
            String permission = role + ":" + suffix;
            jdbc.update("insert into t_permission(name, type, permission_key, status, is_deleted) values (?, 3, ?, ?, ?)",
                    "sync test", permission, suffix.equals("disabled") ? 1 : 0, suffix.equals("deleted"));
            Long permissionId = jdbc.queryForObject("select id from t_permission where permission_key = ?", Long.class, permission);
            jdbc.update("insert into t_role_permission_rel(role_id, permission_id, is_deleted) values (?, ?, ?)",
                    roleId, permissionId, suffix.equals("unlinked"));
        }
        assertThat(mapper.selectEnabledRolePermissions().stream().filter(r -> role.equals(r.getRoleKey()))
                .map(RolePermissionDO::getPermissionKey).filter(java.util.Objects::nonNull))
                .containsExactly(role + ":enabled");
        jdbc.update("update t_role set status = 1 where id = ?", roleId);
        assertThat(mapper.selectEnabledRolePermissions()).noneMatch(r -> role.equals(r.getRoleKey()));
        jdbc.update("update t_role set status = 0, is_deleted = 1 where id = ?", roleId);
        assertThat(mapper.selectEnabledRolePermissions()).noneMatch(r -> role.equals(r.getRoleKey()));
        // 测试事务自动回滚；这些临时权限不会发布到正式缓存。
    }
}
