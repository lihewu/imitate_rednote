# 短信验证码接口

`POST /verification/code/send`，请求头 `Content-Type: application/json`：

```json
{"phone":"18019939108"}
```

接收大陆 11 位手机号。成功响应：

```json
{"success":true,"errorCode":null,"message":null,"data":null}
```

验证码在有效期内重复请求：

```json
{"success":false,"errorCode":"AUTH-20000","message":"请求太频繁，请3分钟后再试","data":null}
```

参数错误返回 HTTP 400 和通用参数错误码；业务失败返回 HTTP 200，通过 `success` 和 `errorCode` 判断。短信未启用、供应商拒绝或调用失败返回 `AUTH-20001`，提示“短信发送失败，请稍后重试”。

## Redis 行为

- 使用 `StringRedisTemplate`，键为 `verification_code:<手机号>`，值为六位数字字符串，保留前导零。
- 使用 Hutool 的安全随机数生成器生成验证码，不返回给前端，也不记录到日志。
- `SET NX` 搭配 180 秒过期时间，原子判断并预存验证码，只有抢占成功的请求调用短信服务，避免“先查再存”的并发重复发送。
- 短信失败时通过 Lua 比较当前值后删除本次验证码；Redis 清理失败则等待自动过期。有效期从预存时开始计算。
- 供应商接受请求不代表手机已收到短信；网络超时也可能发生在供应商已接受请求之后。

## 阿里云配置

在应用运行环境设置以下变量后重启：

| 环境变量 | 值 |
| --- | --- |
| `ALIYUN_SMS_ENABLED` | `true`，默认 `false` |
| `ALIYUN_ACCESS_KEY_ID` | 有短信发送权限的 AccessKey ID |
| `ALIYUN_ACCESS_KEY_SECRET` | 对应密钥 |
| `ALIYUN_SMS_SIGN_NAME` | 已审核通过的短信签名 |
| `ALIYUN_SMS_TEMPLATE_CODE` | 已审核通过的验证码模板编号 |

模板变量为 `${code}`，有效期文案应为 3 分钟。密钥不应写入代码或提交到仓库。默认关闭时应用可以启动，但接口明确返回发送失败。

另需通过现有 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 等变量连接 Redis。本次未实现验证码登录消费接口。

实现参考：[阿里云短信 Java SDK](https://help.aliyun.com/en/sms/developer-reference/using-java-openapi-example)、[Hutool RandomUtil](https://plus.hutool.cn/apidocs/cn/hutool/core/util/RandomUtil.html)。
