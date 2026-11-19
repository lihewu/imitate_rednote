# rednote

rednote 是一个 Java 17 / Maven 多模块后端项目。当前仓库实现了**认证服务和 API 网关**：短信验证码、登录与自动注册、共享登录态、角色权限缓存以及网关登录校验。本文记录的是仓库内的实现与默认配置；实际部署还受 Nacos 和外部配置影响。

## 模块与代码入口

| 模块 | 职责 | 入口 |
| --- | --- | --- |
| `rednote-auth` | Spring MVC 认证服务；处理登录和验证码，读写 MySQL，向 Redis 写入登录态、用户角色和角色权限 | [RednoteAuthApplication](rednote-auth/src/main/java/com/rednote/rednoteauth/RednoteAuthApplication.java) |
| `rednote-gateway` | Spring Cloud Gateway / WebFlux；通过 Nacos 发现认证服务，用 Sa-Token 读取共享登录态 | [RednoteGatewayApplication](rednote-gateway/src/main/java/com/rednote/gateway/RednoteGatewayApplication.java) |
| `rednote-framework/rednote-common` | 通用响应、业务异常、JSON 工具、日期常量和手机号校验 | [公共类目录](rednote-framework/rednote-common/src/main/java/com/rednote/framework/common) |
| `rednote-framework/rednote-spring-boot-starter-biz-operationlog` | 认证服务引入的 `@ApiOperationLog` AOP 组件；自动装配，目前业务方法未加此注解 | [日志组件目录](rednote-framework/rednote-spring-boot-starter-biz-operationlog/src/main/java/com/rednote/framework/biz/operationlog) |

常见改动从这里开始：

| 要改什么 | 先读什么 |
| --- | --- |
| 登录参数、注册、密码校验 | [UserController](rednote-auth/src/main/java/com/rednote/rednoteauth/controller/UserController.java) → [UserServiceImpl](rednote-auth/src/main/java/com/rednote/rednoteauth/service/impl/UserServiceImpl.java) → [UserDOMapper.xml](rednote-auth/src/main/resources/mapper/UserDOMapper.xml) |
| 短信发送、验证码有效期、限频 | [VerificationCodeController](rednote-auth/src/main/java/com/rednote/rednoteauth/controller/VerificationCodeController.java) → [VerificationCodeServiceImpl](rednote-auth/src/main/java/com/rednote/rednoteauth/service/impl/VerificationCodeServiceImpl.java)、[ThreadPoolConfig](rednote-auth/src/main/java/com/rednote/rednoteauth/config/ThreadPoolConfig.java)、[短信组件](rednote-auth/src/main/java/com/rednote/rednoteauth/sms) |
| 网关路由、白名单、登录失败响应 | [网关路由配置](rednote-gateway/src/main/resources/application.yml)、[SaTokenConfigure](rednote-gateway/src/main/java/com/rednote/gateway/auth/SaTokenConfigure.java) |
| 权限快照、角色查询 | [RolePermissionMapper.xml](rednote-auth/src/main/resources/mapper/RolePermissionMapper.xml) → [RolePermissionSyncServiceImpl](rednote-auth/src/main/java/com/rednote/rednoteauth/service/impl/RolePermissionSyncServiceImpl.java) → [StpInterfaceImpl](rednote-gateway/src/main/java/com/rednote/gateway/auth/StpInterfaceImpl.java) |
| 认证响应、异常映射、JSON 格式 | [Response](rednote-framework/rednote-common/src/main/java/com/rednote/framework/common/response/Response.java)、[GlobalExceptionHandler](rednote-auth/src/main/java/com/rednote/rednoteauth/exception/GlobalExceptionHandler.java)、[JacksonConfig](rednote-auth/src/main/java/com/rednote/rednoteauth/config/JacksonConfig.java) |
| Nacos 刷新、告警示例 | [认证配置](rednote-auth/src/main/resources/config/application.yml)、[网关 bootstrap.yml](rednote-gateway/src/main/resources/bootstrap.yml)、[AlarmConfig](rednote-auth/src/main/java/com/rednote/rednoteauth/alarm/AlarmConfig.java) |

## 接口与请求链路

客户端访问网关的 `/auth/**`。全局过滤器按原始路径放行下表的两个公开接口，其余路径执行 `StpUtil.checkLogin()`。匹配认证路由的请求通过校验后执行 `StripPrefix=1`，再通过 `lb://rednote-auth` 转发给认证服务。

| 方法 | 网关路径 | 认证服务直连路径 | 请求及结果 |
| --- | --- | --- | --- |
| `POST` | `/auth/verification/code/send` | `/verification/code/send` | JSON：`phone` 为大陆 11 位手机号。网关放行；`success=true` 只表示任务已提交给线程池，不代表短信已送达。 |
| `POST` | `/auth/user/login` | `/user/login` | JSON：`phone`、整数 `type`。`type=1` 传六位字符串 `code`，保留前导零；`type=2` 传非空白 `password`，UTF-8 不超过 72 字节。网关放行；成功时 `data` 是 Token。 |
| `GET` | `/auth/alarm` | `/alarm` | 无请求参数；经网关需要登录。按 `alarm.type=sms` 或 `mail` 写一条测试日志，**不实际发短信或邮件**。 |

登录后，通过 `satoken` 请求头携带 Token，不需要 `Bearer` 前缀。认证服务的统一响应包含 `success`、`errorCode`、`message`、`data`。业务错误通常仍是 HTTP 200：`AUTH-20000` 请求过频、`AUTH-20001` 发送失败或短信未启用、`AUTH-20002` 验证码错误或已过期、`AUTH-20003` 手机号或密码错误、`AUTH-20004` 账号不可用。参数不合法通常是 HTTP 400 / `10001`，未预期异常是 HTTP 500 / `10000`。网关的登录校验失败会在转发前返回 Sa-Token 的 `SaResult`（`code`、`msg`、`data`）；过滤器没有显式设置 HTTP 401，不要把其中的 `code` 当成 HTTP 状态。

### 验证码、注册与登录

1. **验证码发送**：先检查短信配置是否启用，再生成六位验证码。用 Redis `SET NX` 加 180 秒有效期预占同一个手机号的验证码，避免并发重复发送。后台线程发送前复查验证码仍有效，经阿里云号码认证发送；入队失败或后台发送失败时尝试按值清理，避免删除新验证码。线程池队列在进程内，等待时间计入有效期。
2. **验证码登录和自动注册**：Lua 原子比较并消费验证码，错误验证码不删除已有值。随后查询 `t_user`；手机号未注册时，从 Redis 取 rednote 编号，在独立 MySQL 事务插入并回填数据库主键。手机号与 rednote 编号的唯一约束由数据库提供。新账号没有初始密码；当前未提供设置或重置密码、注销或退出登录的业务接口。验证码消费后若后续步骤失败，需要重新获取验证码，MySQL 与 Redis 不在同一事务中。
3. **密码登录**：仅用于已有 BCrypt 密码哈希的账号，不支持明文密码。两种登录都拒绝禁用或逻辑删除的账号。业务代码仅在角色键缺失时补齐 `common_user`，保留既有角色，再以 `t_user.id` 签发 Token。仓库默认 Token 有效期 30 天，同一账号可并发登录，每次签发独立 Token。

### Redis 数据契约与权限边界

| 键 | 数据形态与用途 |
| --- | --- |
| `verification_code:<phone>` | String，六位验证码，180 秒有效期；登录时一次性消费。 |
| `rednote_id_generator` | String，自增计数器，无过期；初次分配 `10001`，不等同于 MySQL 自增主键。 |
| `user:roles:<t_user.id>` | String，JSON 角色数组，例如 `["common_user"]`，无过期。认证服务写入，网关按用户主键读取。 |
| `role:permissions` | Hash，field 为角色编码，value 为 JSON 权限编码数组。无过期；认证服务发布快照，网关读取。 |
| `{role:permissions}:sync:lock` | 角色权限同步的默认租期锁，30 秒，无自动续期。 |
| Sa-Token 框架键 | `sa-token-redis-template` 保存 Token 与 Session，例如 `satoken:login:token:<token>`。两服务必须共享 Redis 实例、库和兼容的 Sa-Token 配置。 |

认证服务启动时，[RolePermissionCacheInitializer](rednote-auth/src/main/java/com/rednote/rednoteauth/config/RolePermissionCacheInitializer.java) 调用同步服务，从 `t_role`、`t_role_permission_rel`、`t_permission` 查询有效数据。快照中的权限去重排序，无权限角色记为 `[]`。同步用 Lua 校验锁所有者后原子替换整份 Hash，过期任务不能覆盖新快照；没有有效角色则删除该 Hash。抢不到锁、查库失败等同步错误会让认证服务启动失败。当前没有定时同步或对外刷新接口；修改数据库后需重新同步。

**权限数据已接入网关，但当前路由只检查是否登录。** `StpInterfaceImpl` 能按用户角色聚合权限，不代表每条路由已经调用权限检查。认证侧的 `auth.role-permission-cache-key` 可以换键，但网关当前读的是固定 `role:permissions`，单独改认证侧会读不到快照。仓库也没有用户角色关系管理接口，角色键丢失后登录只能补齐默认角色。部署时应通过网络策略限制认证服务直连，不能把网关拦截当作认证服务自己的校验。

## 本地运行

- **工具链**：JDK 17、Maven。版本由[根 POM](pom.xml)统一管理：Spring Boot `3.0.2`、Spring Cloud `2022.0.0`、Spring Cloud Alibaba `2022.0.0.0`、Sa-Token `1.46.0`。仓库没有 Maven Wrapper。
- **MySQL**：`dev` 默认连接 `127.0.0.1:3306/rednote`，通过 [application-dev.yml](rednote-auth/src/main/resources/config/application-dev.yml) 配置 Druid。仓库有 Mapper，但没有建表脚本或迁移工具；需事先准备与 Mapper 匹配的 `t_user`、`t_role`、`t_role_permission_rel`、`t_permission`。不同环境可用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖认证服务配置。
- **Redis**：按 Sa-Token 当前版本要求使用 Redis 6.0 或更新版本。`dev` 默认 `127.0.0.1:6379`、DB `0`，通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 配置。排查会话和权限时，先确认两服务实际连到同一个库。
- **Nacos**：负责服务发现和配置中心。先启动 Nacos，再启动两个应用。默认客户端地址 `127.0.0.1:8848`，客户端还需要 gRPC 端口 `9848`。Windows 可用 [ensure-nacos.ps1](scripts/ensure-nacos.ps1) 启动已安装的本地实例，传入实际 `-NacosHome`；脚本要求该目录包含 `start-standalone.cmd`，不负责安装或初始化远端配置。
- **短信**：默认关闭，发送接口会返回 `AUTH-20001`。使用真实短信时配置 `ALIYUN_SMS_ENABLED`、`ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`ALIYUN_SMS_SIGN_NAME`、`ALIYUN_SMS_TEMPLATE_CODE`。告警示例的 `alarm.type` 与短信发送开关无关。

### Nacos 配置与启动顺序

| 应用 | 默认端口 | `dev` 配置来源 |
| --- | --- | --- |
| `rednote-auth` | `8081` | [application.yml](rednote-auth/src/main/resources/config/application.yml) 使用 `spring.config.import` 导入 `rednote-auth-dev.yml`，不是 `optional:` 导入，并开启刷新。 |
| `rednote-gateway` | `8082` | [bootstrap.yml](rednote-gateway/src/main/resources/bootstrap.yml) 启用 Nacos Bootstrap，尝试加载 `rednote-gateway`、`rednote-gateway.yml`、`rednote-gateway-dev.yml`，这些不是必须发布的条目；仓库默认路由在本地 [application.yml](rednote-gateway/src/main/resources/application.yml)。 |

1. 准备 MySQL、Redis 和 Nacos；在 Nacos 中核对命名空间 **ID**（默认 `rednote`）、组（默认 `DEFAULT_GROUP`）以及认证服务的配置导入 Data ID。两服务都读取 `NACOS_PASSWORD`，配置无默认值；`NACOS_USERNAME`、`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE`、`NACOS_GROUP` 可覆盖配置中心设置。
2. 若使用非默认 Nacos 地址、命名空间或组，**还要单独覆盖认证服务的 discovery 配置**：`SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`、`SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE`、`SPRING_CLOUD_NACOS_DISCOVERY_GROUP`。网关的 `NACOS_*` 同时作用于 config 和 discovery，认证侧的地址、命名空间、组不是这样绑定的。
3. 从 IDE 先运行 `RednoteAuthApplication`，确认角色权限同步成功且 `rednote-auth` 已在 Nacos 注册，再运行 `RednoteGatewayApplication`。也可从根目录 `mvn -Dmaven.test.skip=true package` 打包，然后在分别配置好环境变量的终端运行以下命令。此打包命令跳过测试编译和执行，不作为测试通过的证明。两个应用的端口都可用 `SERVER_PORT` 覆盖，分别配置以免冲突。

   ```text
   java -jar rednote-auth/target/rednote-auth-0.0.1-SNAPSHOT.jar
   java -jar rednote-gateway/target/rednote-gateway-0.0.1-SNAPSHOT.jar
   ```

4. 通过 `http://localhost:8082/auth/user/login` 登录，再带 `satoken` 请求头访问 `GET /auth/alarm` 验证网关转发和共享会话。真实验证码登录需启用短信；密码登录需已有 BCrypt 账号。可在 Nacos 刷新 `alarm.type` 后再次调用，观察 `@RefreshScope` 切换告警日志实现。

`prod` 下认证服务的 [application-prod.yml](rednote-auth/src/main/resources/config/application-prod.yml) 仅有注释，不是现成的部署配置；切换 profile 后需要提供对应的 Nacos 配置与数据源、Redis 等参数。不要在 README 或提交中放真实凭据。

## 测试与日常排查

| 测试源码 | 覆盖范围 |
| --- | --- |
| [UserLoginIntegrationTests](rednote-auth/src/test/java/com/rednote/rednoteauth/UserLoginIntegrationTests.java) | MockMvc 加真实 MySQL、Redis，验证注册、事务回滚、验证码一次性消费、BCrypt 密码和 Token 持久化。不发送真实短信。 |
| [RolePermissionSyncIntegrationTests](rednote-auth/src/test/java/com/rednote/rednoteauth/RolePermissionSyncIntegrationTests.java) | SQL 过滤、启动同步、快照替换、Redis 锁互斥和失效持有者保护。 |
| [网关鉴权测试](rednote-gateway/src/test/java/com/rednote/gateway/auth) | 使用本地测试替身验证登录过滤和权限缓存读取，不覆盖真实 Nacos 路由联调。 |

在仓库根目录运行认证集成测试（需要 `dev` 的 MySQL、Redis 和默认配置链路所需的 Nacos）：

```text
mvn -pl rednote-auth -am "-Dtest=UserLoginIntegrationTests,RolePermissionSyncIntegrationTests" -Dsurefire.failIfNoSpecifiedTests=false test
```

请使用开发或测试库。认证集成测试启动时会刷新角色权限缓存；登录测试会消耗全局编号，即使测试用户随后被清理。网关测试可从 `mvn -pl rednote-gateway -am test` 运行，但当前 [SaTokenConfigureTest 的导入](rednote-gateway/src/test/java/com/rednote/gateway/auth/SaTokenConfigureTest.java#L4) 与项目锁定的 Sa-Token 1.46.0 类路径不一致，需先修正测试编译问题。`AppTest` 只是占位，不能代替鉴权验证。

- **认证服务启动失败**：先核对 Nacos 账号、命名空间 ID、组和 Data ID，再检查 MySQL/Redis 连接、角色权限表和同步锁。角色权限同步属于启动流程，不会在失败后静默跳过。
- **网关找不到下游**：看 Nacos 中是否存在名为 `rednote-auth` 的健康实例，检查两服务实际 discovery 命名空间与组是否一致，以及实际路由是否保留了 `StripPrefix=1`。
- **登录成功但网关拒绝后续请求**：核对 `satoken` 请求头、登录是否过期，以及两服务的 Redis 库和 `sa-token` 配置；排查权限数据时再核对 `user:roles:<id>` 和 `role:permissions`。
- **验证码接口成功但收不到短信**：检查认证服务日志中的异步任务、验证码是否仍有效及阿里云返回。认证文件日志默认在 `./logs/rednote-auth/`，可通过 `LOGGING_FILE_PATH` 调整；详细滚动策略见 [logback-spring.xml](rednote-auth/src/main/resources/logback-spring.xml)。
- **要重新生成用户表映射**：先阅读 [generatorConfig.xml](rednote-auth/src/main/resources/generatorConfig.xml)。生成器会改动映射文件，重新生成前核对开发库连接，保留 `selectByPhone`、主键回填和手工调整的实体代码。

专题文档：[用户登录](rednote-auth/USER_LOGIN.md) · [短信验证码](rednote-auth/VERIFICATION_CODE.md) · [角色权限同步](rednote-auth/ROLE_PERMISSIONS.md) · [Sa-Token 与 Redis](rednote-auth/SA_TOKEN_REDIS.md)。专题文档侧重认证服务；网关接入后的路由和权限边界以本 README 对应的配置和源码为准。以后改动接口、路由、共享 Redis 键或启动前提时，记得同步更新这里的入口和说明。
