# 角色权限同步

认证服务启动时由 `RolePermissionCacheInitializer` 调用 `RolePermissionSyncService.syncRolePermissions()`，
从 `t_role`、`t_role_permission_rel`、`t_permission` 联查并全量同步 Redis。

- 仅包含 `status=0` 且未逻辑删除的角色、权限，以及未逻辑删除的关联。
- Redis Key 为 `role:permissions`，类型为 Hash，field 是 `role_key`，value 是权限编码 JSON 数组。
- 权限去重并排序，无权限的有效角色保存 `[]`。
- Lua 原子替换整份 Hash，移除旧角色和已撤销权限；数据库没有有效角色时删除该 Key。
- 同步前使用 Redis 分布式锁，默认锁 Key 为 `{role:permissions}:sync:lock`，
  `SET NX` 与 30 秒租期一起设置，UUID 标识持有者。没有自动续期，任务超过租期须重试。
  未取得锁立即报错（启动同步时会阻止该实例启动），不查询数据库或修改缓存。
  发布缓存的 Lua 同时校验所有权，租期失效的旧任务不能覆盖新数据；finally 中用 Lua 比较标识后释放锁，
  不会误删其他实例的锁。释放失败时依靠租期清理。
  锁 Key 与缓存 Key 使用相同 hash slot，支持 Redis Cluster 的多 Key Lua。
  这是基于单个 Redis 主节点的租期锁，不提供异步主从故障切换期间的强一致互斥保证。
- 数据库查询或序列化失败不会修改旧缓存；启动同步失败则启动失败，不静默忽略错误。
- 不设置过期时间。修改数据库角色权限后，重启认证服务或从受信任的业务流程调用同步 Service。
  当前没有定时同步或对外刷新接口，也未接入 Sa-Token 的 `StpInterface` 权限读取。
- 可通过 `auth.role-permission-cache-key` 配置覆盖缓存 Key，默认使用 `RedisKeyConstants.ROLE_PERMISSIONS_KEY`。

在 Another Redis Desktop Manager 中选择应用连接的数据库（默认 DB0），刷新并搜索 `role:permissions`，
或在 Redis 控制台执行：

```redis
HGETALL role:permissions
HGET role:permissions common_user
```

角色编码以数据库为准；没有有效角色时 Key 不存在。
此缓存与 `user:roles:<用户ID>` 分工不同：前者保存角色拥有的权限，后者保存用户拥有的角色。

验证命令：

```shell
mvn -pl rednote-auth -am -Dtest=RolePermissionSyncIntegrationTests,UserLoginIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

需要本地 MySQL 和 Redis。启动测试会刷新正式角色权限缓存；快照替换测试使用随机隔离 Key 并清理，
数据库过滤测试使用事务回滚，不改动原有角色权限数据。
