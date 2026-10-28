package com.rednote.rednoteauth.service;

public interface RolePermissionSyncService {
    /** 从数据库全量刷新角色权限缓存，返回有效角色数量。 */
    int syncRolePermissions();
}
