package com.rednote.rednoteauth.service;

import cn.hutool.core.util.RandomUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import com.rednote.rednoteauth.sms.SmsSender;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class VerificationCodeService {
    private static final String KEY_PREFIX = "verification_code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final DefaultRedisScript<Long> REMOVE_OWN_CODE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SmsSender smsSender;

    public VerificationCodeService(StringRedisTemplate redisTemplate, SmsSender smsSender) {
        this.redisTemplate = redisTemplate;
        this.smsSender = smsSender;
    }

    public void send(String phone) {
        String key = KEY_PREFIX + phone;
        // 使用 Hutool 的安全随机数生成器，保留前导零，始终生成六位数字。
        String code = String.format(Locale.ROOT, "%06d", RandomUtil.getSecureRandom().nextInt(1_000_000));
        // 将存在判断、写入及过期时间设置合为原子操作，防止并发重复发送。
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(key, code, CODE_TTL);
        if (Boolean.FALSE.equals(reserved)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        if (!Boolean.TRUE.equals(reserved)) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
        try {
            smsSender.sendVerificationCode(phone, code);
        } catch (RuntimeException exception) {
            // 发送失败允许重试；只删除本次写入的验证码，避免误删已更新的值。
            try {
                redisTemplate.execute(REMOVE_OWN_CODE, List.of(key), code);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FAILED, exception);
        }
    }
}
