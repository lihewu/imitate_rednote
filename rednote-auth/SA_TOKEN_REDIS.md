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
mvn -pl rednote-auth -am -Dtest=UserLoginIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

测试需要 MySQL 和 Redis，覆盖注册、登录、验证码消费和 Token 写入 Redis，并清理自己的测试数据。
使用 `POST /user/login` 登录，返回的 `data` 即 Token，后续请求通过 `satoken` 请求头携带。
旧演示接口 `/login`、`/isLogin`、`/test` 已删除。完整请求示例见 [用户登录说明](USER_LOGIN.md)。

参考：[Sa-Token 官方 Redis 整合说明](https://sa-token.com/up/integ-redis.html)。
