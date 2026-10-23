# 短信验证码接口

`POST /verification/code/send`，请求头 `Content-Type: application/json`：

```json
{"phone":"18019939108"}
```

接收大陆 11 位手机号。任务成功提交到本地异步线程池时返回以下响应（不代表短信发送成功）：

```json
{"success":true,"errorCode":null,"message":null,"data":null}
```

验证码在有效期内重复请求：

```json
{"success":false,"errorCode":"AUTH-20000","message":"请求太频繁，请3分钟后再试","data":null}
```

参数错误返回 HTTP 400 和通用参数错误码；业务失败返回 HTTP 200，通过 `success` 和 `errorCode` 判断。线程池满或关闭导致提交失败时返回 `AUTH-20001`。短信未启用时在任务提交前返回 AUTH-20001，并提示检查运行配置。供应商拒绝或调用失败发生在后台，记录脱敏日志并清理本次验证码，无法修改已返回的 HTTP 响应。

## 异步线程池

`ThreadPoolConfig` 提供 `smsTaskExecutor`：核心线程 4、最大线程 8、队列容量 100、空闲存活 60 秒，线程名前缀 `sms-send-`。任务满时拒绝提交，关闭时等待任务完成，最多等待 30 秒。通过 `execute` 显式提交，无需 `@Async`。

此线程池是内存队列，进程异常退出可能丢失任务。任务执行前会检查验证码是否仍在 Redis 中，已失效或被替换则跳过发送。有效期包含排队时间。

## Redis 行为

- 使用 `StringRedisTemplate`，键为 `verification_code:<手机号>`，值为六位数字字符串，保留前导零。
- 使用 Hutool 的安全随机数生成器生成验证码，不返回给前端，也不记录到日志。
- `SET NX` 搭配 180 秒过期时间，原子判断并预存验证码，只有抢占成功的请求调用短信服务，避免“先查再存”的并发重复发送。
- 短信失败时通过 Lua 比较当前值后删除本次验证码；Redis 清理失败则等待自动过期。有效期从预存时开始计算。
- 供应商接受请求不代表手机已收到短信；网络超时也可能发生在供应商已接受请求之后。

## 阿里云号码认证配置

短信相关代码统一放置于 `com.rednote.rednoteauth.sms`：

- `AliyunAccessKeyProperties`：绑定 `sms.aliyun.access-key-id` 和 `sms.aliyun.access-key-secret`。
- `SmsProperties`：绑定短信开关、签名和模板。
- `AliyunSmsClientConfig`：启用短信时创建名为 `aliyunSmsClient` 的 Spring 客户端 Bean，并注入 `AliyunSmsHelper`；关闭时提供明确报错的 `SmsSender`。
- `AliyunSmsHelper`：实现 `SmsSender`，封装号码认证短信请求、响应检查和异常转换。业务层继续通过 `SmsSender` 接口调用，异步线程池由业务层管理。

配置仍使用下述环境变量，无需在 Java 代码中填写密钥。

使用 `com.aliyun:dypnsapi20170525:2.0.0`，Endpoint 为 `dypnsapi.aliyuncs.com`，调用 `SendSmsVerifyCode`。需开通号码认证的短信认证功能，并为调用账号授予 `dypns:SendSmsVerifyCode` 权限。

当前沿用本地生成六位验证码、Redis 保存和校验的设计，直接将验证码填入 `TemplateParam`，而非使用 `##code##` 占位符。因此不能通过阿里云 `CheckSmsVerifyCode` 校验此验证码；后续登录接口应读取 Redis 校验。请求设置 `ValidTime=180`、`Interval=180`、`ReturnVerifyCode=false`，并同时检查响应 `Code=OK` 和 `Success=true`。

在应用运行环境设置以下变量后重启：

| 环境变量 | 值 |
| --- | --- |
| `ALIYUN_SMS_ENABLED` | `true`，默认 `false` |
| `ALIYUN_ACCESS_KEY_ID` | 有短信发送权限的 AccessKey ID |
| `ALIYUN_ACCESS_KEY_SECRET` | 对应密钥 |
| `ALIYUN_SMS_SIGN_NAME` | 号码认证控制台赠送的短信签名 |
| `ALIYUN_SMS_TEMPLATE_CODE` | 与赠送签名配套的验证码模板编号 |

选择包含 `${code}` 和 `${min}` 的赠送模板，分别传入本地验证码和 `3`。原短信服务的签名、`SMS_...` 模板编号不能直接当作号码认证配置使用，请从号码认证控制台复制实际配置。现有环境变量名称保留，无需重新保存 AccessKey；需确认该账号具备新服务权限。密钥不应写入代码或提交到仓库。默认关闭时应用可以启动，但发送接口会直接返回服务未启用。后台日志记录掩码手机号、供应商错误码和请求 ID；已受理不等于运营商已送达。

另需通过现有 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 等变量连接 Redis。本次未实现验证码登录消费接口。

实现参考：[号码认证 SendSmsVerifyCode](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-sendsmsverifycode)、[Hutool RandomUtil](https://plus.hutool.cn/apidocs/cn/hutool/core/util/RandomUtil.html)。
