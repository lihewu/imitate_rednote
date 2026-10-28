package com.rednote.rednoteauth.config;

import com.rednote.rednoteauth.service.RolePermissionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时加载角色权限；同步失败直接阻止启动，避免静默使用过时授权数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionCacheInitializer implements ApplicationRunner {
    private final RolePermissionSyncService syncService;

    @Override
    public void run(ApplicationArguments args) {
        int roleCount = syncService.syncRolePermissions();
        log.info("角色权限缓存同步完成，角色数={}", roleCount);
    }
}
