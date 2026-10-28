# Sa-Token 与 Redis

使用 `sa-token-redis-template`，版本由根 POM 的 `sa-token.version` 统一管理。
Starter 自动注册 Redis 版 `SaTokenDao`，登录 Token 和 Session 存入 Redis，多实例共享同一 Redis 数据库即可共享登录状态。
无需额外声明 DAO Bean；业务代码继续调用 `StpUtil`。

## 运行配置

当前版本 1.46.0 要求 Redis **6.0 或以上**（更新会话时使用 `SET KEEPTTL`）。
复用 Spring Boot 的 `spring.data.redis` 配置；dev 环境已有以下环境变量：

| 环境变量 | 默认值 |
| --- | --- |
| `REDIS_HOST` | `127.0.0.1` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | 空 |
| `REDIS_DATABASE` | `0` |

prod 环境需通过外部配置提供 `spring.data.redis`，或设置 Spring Boot 标准变量
`SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_DATA_REDIS_PASSWORD`、`SPRING_DATA_REDIS_DATABASE`。
现有 `sa-token` 配置保持有效：请求头为 `satoken`，有效期 30 天，每次登录签发独立 Token。
原内存中的登录状态不会迁移，切换后需要重新登录。Redis 自身重启后的数据保留取决于其持久化配置。

## 验证

启动 Redis 后，在仓库根目录运行（测试仅创建随机测试键并在结束后清理）：

```shell
mvn -pl rednote-auth -am -Dtest=SaTokenRedisTests,RedisTests -Dsurefire.failIfNoSpecifiedTests=false test
```

测试覆盖 Redis DAO 自动装配、Token 读写删除、Session 序列化，以及更新后保留 TTL。
也可通过现有演示接口 `POST /login` 登录，携带返回的 `satoken` 请求头访问 `GET /isLogin`，
重启应用后再次访问验证登录状态保留。该登录接口仍是演示账号实现。

参考：[Sa-Token 官方 Redis 整合说明](https://sa-token.com/up/integ-redis.html)。
