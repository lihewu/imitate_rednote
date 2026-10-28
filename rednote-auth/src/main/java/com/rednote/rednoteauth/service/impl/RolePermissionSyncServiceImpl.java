package com.rednote.rednoteauth.service.impl;

import com.rednote.framework.common.util.JsonUtils;
import com.rednote.rednoteauth.constant.RedisKeyConstants;
import com.rednote.rednoteauth.domain.dataobject.RolePermissionDO;
import com.rednote.rednoteauth.domain.mapper.RolePermissionMapper;
import com.rednote.rednoteauth.service.RolePermissionSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class RolePermissionSyncServiceImpl implements RolePermissionSyncService {
    private final RolePermissionMapper mapper;
    private final StringRedisTemplate redis;
    private final String cacheKey;
    private static final Duration LOCK_LEASE = Duration.ofSeconds(30);

    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]); end; return 0;",
            Long.class);

    // 参数全部在执行前构造完成；脚本执行期间其他客户端不会读到半份缓存。
    private static final DefaultRedisScript<Long> REPLACE_PERMISSIONS = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[2]) ~= ARGV[1] then return -1; end; "
                    + "redis.call('del', KEYS[1]); "
                    + "for i = 2, #ARGV, 2 do redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1]); end; "
                    + "return (#ARGV - 1) / 2;", Long.class);

    public RolePermissionSyncServiceImpl(RolePermissionMapper mapper, StringRedisTemplate redis,
            @Value("${auth.role-permission-cache-key:" + RedisKeyConstants.ROLE_PERMISSIONS_KEY + "}") String cacheKey) {
        this.mapper = mapper;
        this.redis = redis;
        this.cacheKey = cacheKey;
    }

    @Override
    public int syncRolePermissions() {
        String lockKey = RedisKeyConstants.buildRolePermissionsLockKey(cacheKey);
        String owner = UUID.randomUUID().toString();
        // SET NX PX：加锁和设置租期一次完成，进程崩溃后不会留下永久锁。
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, owner, LOCK_LEASE))) {
            throw new IllegalStateException("角色权限同步正在执行，请稍后重试");
        }
        try {
            return syncUnderLock(lockKey, owner);
        } finally {
            try {
                // 只释放自己的锁，避免租期结束后误删另一实例的新锁。
                redis.execute(RELEASE_LOCK, List.of(lockKey), owner);
            } catch (RuntimeException exception) {
                // 锁会在租期结束后释放；不覆盖原始同步异常。
                log.warn("角色权限同步锁释放失败，等待租期结束，errorType={}", exception.getClass().getSimpleName());
            }
        }
    }

    private int syncUnderLock(String lockKey, String owner) {
        List<RolePermissionDO> rows = Objects.requireNonNull(mapper.selectEnabledRolePermissions(),
                "Role permission query must not return null");
        Map<String, Set<String>> permissionsByRole = new TreeMap<>();
        for (RolePermissionDO row : rows) {
            if (row.getRoleKey() == null || row.getRoleKey().isBlank()) {
                throw new IllegalStateException("角色编码不能为空");
            }
            Set<String> permissions = permissionsByRole.computeIfAbsent(row.getRoleKey(), key -> new TreeSet<>());
            if (row.getPermissionKey() != null && !row.getPermissionKey().isBlank()) {
                permissions.add(row.getPermissionKey());
            }
        }
        List<String> args = new ArrayList<>();
        args.add(owner);
        permissionsByRole.forEach((role, permissions) -> {
            args.add(role);
            args.add(JsonUtils.toJsonString(permissions));
        });
        // 校验锁所有权和发布快照在同一 Lua 脚本内，过期持有者不能覆盖新快照。
        Long count = redis.execute(REPLACE_PERMISSIONS, List.of(cacheKey, lockKey), args.toArray());
        if (Long.valueOf(-1).equals(count)) {
            throw new IllegalStateException("角色权限同步锁已失效，本次结果未写入，请重试");
        }
        if (count == null || count != permissionsByRole.size()) {
            throw new IllegalStateException("角色权限缓存同步失败");
        }
        return count.intValue();
    }
}
