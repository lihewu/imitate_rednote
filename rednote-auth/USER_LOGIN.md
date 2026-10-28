# 用户注册与登录

`POST /user/login`，请求头 `Content-Type: application/json`。

验证码登录（先调用 `/verification/code/send` 获取短信验证码）：

```json
{"phone":"18011119108","code":"218603","type":1}
```

密码登录：

```json
{"phone":"18011119108","password":"你的密码","type":2}
```

成功返回：

```json
{"success":true,"message":null,"errorCode":null,"data":"实际Token"}
```

后续请求在 `satoken` 请求头携带该 Token。

- `type=1`：校验六位数字验证码。Redis Lua 将比较和删除合为原子操作；错误验证码不删除正确值，已消费或过期的验证码不能再次登录。未注册手机号自动创建用户，已有用户复用原用户 ID。
- `type=2`：手机号必须已注册且有 BCrypt 密码哈希，使用 BCrypt 校验，不支持明文或 MD5 密码。密码必填且 UTF-8 编码不超过 72 字节。
- 验证码注册通过 Redis 递增生成 rednote ID，并设置默认昵称与创建时间，初始状态为正常，密码为空。此接口不会通过验证码登录请求中的 `password` 设置或修改密码；密码设置/重置接口不在本次范围内。因此新注册账号应继续使用验证码登录，已有 BCrypt 密码的账号可使用密码登录。
- 手机号唯一索引防止重复注册；编号冲突最多重试三次。注册使用 `TransactionTemplate` 编程式事务，每次插入及主键回填校验在独立的 `REQUIRES_NEW` 事务中执行，异常自动回滚；唯一键冲突在回滚后查询已有账号或重试。事务提交成功后才写入角色缓存并签发 Token。
- 封禁（`status != 0`）及逻辑删除（`is_deleted = 1`）账号不签发 Token。
- 验证码消费后若数据库或 Redis 登录态写入失败，需要重新获取验证码；MySQL 与 Redis 之间没有分布式事务。

| 错误码 | 含义 |
| --- | --- |
| `10001` | 参数不合法，HTTP 400 |
| `AUTH-20002` | 验证码错误或已过期 |
| `AUTH-20003` | 手机号或密码错误（包括未设置密码） |
| `AUTH-20004` | 账号不可用 |

业务错误通过 HTTP 200 的 `success=false` 表达；系统异常返回 HTTP 500。
当前未增加登录失败次数限制，部署公网前应配置登录限流。

## Redis 编号及角色缓存

- `rednote_id_generator`：String 类型的全局计数器。首次注册时通过 Lua 原子执行 `SETNX 10000` 和 `INCR`，首次分配 `10001`。已有计数不覆盖，不设置过期时间，编号写入 `t_user.rednote_id`。
- `t_user.id` 仍由 MySQL 自增生成。Mapper XML 的 `useGeneratedKeys="true" keyProperty="id" keyColumn="id"` 会将主键回填至 `UserDO.id`，用于登录和角色缓存。
- `user:roles:<数据库用户ID>`：String 类型，内容为 JSON 角色数组，例如 `["common_user"]`。默认角色由 `RoleConstants.COMMON_USER` 定义。登录时仅在缓存缺失时补齐默认角色，不覆盖已有值，不设置过期时间。
- 目前没有角色关系表或角色管理接口，角色缓存缺失时只能恢复普通用户角色；后续接入角色管理时应改为从数据库恢复实际角色。
- 生成器需配合 Redis 持久化使用，不应删除、过期或回退计数；注册失败允许出现编号空洞。历史随机编号保持不变，重复登录不会重新分配编号。

在 Redis 控制台查询：

```redis
GET rednote_id_generator
GET user:roles:实际用户ID
```

## 表映射与验证

已从实际 `t_user` 表通过 MyBatis Generator 重新生成实体、Mapper 和 XML，
将 `sex/status` 映射为 `Integer`、`isDeleted` 映射为 `Boolean`，生日使用 `LocalDate`，创建/更新时间使用 `LocalDateTime`。
`UserDO` 使用 Lombok，Mapper 新增 `selectByPhone`。生成器配置已固定枚举字段类型并禁用 XML 合并，避免重复语句。
再次生成后仍需保留 Lombok 整理及手工添加的 `selectByPhone` 接口/XML。

旧测试类与演示控制器已删除，新接口的回归测试为 `UserLoginIntegrationTests`：

```shell
mvn -pl rednote-auth -am clean test
```

需要可用的 MySQL、Redis 及正确的 `REDIS_PASSWORD`。测试使用随机手机号、不会发送短信，并清理测试账号、验证码、角色缓存和会话。测试会消耗全局编号，不会重置生成器。

密码校验参考：[Hutool BCrypt](https://plus.hutool.cn/apidocs/cn/hutool/crypto/digest/BCrypt.html)。
