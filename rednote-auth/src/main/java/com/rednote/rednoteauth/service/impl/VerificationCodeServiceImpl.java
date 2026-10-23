package com.rednote.rednoteauth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.rednote.rednoteauth.constant.RedisKeyConstants;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import com.rednote.rednoteauth.service.VerificationCodeService;
import com.rednote.rednoteauth.sms.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeServiceImpl.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final DefaultRedisScript<Long> REMOVE_OWN_CODE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SmsSender smsSender;
    private final TaskExecutor smsTaskExecutor;

    public VerificationCodeServiceImpl(StringRedisTemplate redisTemplate, SmsSender smsSender,
                                      @Qualifier("smsTaskExecutor") TaskExecutor smsTaskExecutor) {
        this.redisTemplate = redisTemplate;
        this.smsSender = smsSender;
        this.smsTaskExecutor = smsTaskExecutor;
    }

    @Override
    public void send(String phone) {
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
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
            smsTaskExecutor.execute(() -> sendAsync(phone, key, code));
        } catch (RuntimeException exception) {
            removeOwnCode(key, code);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FAILED, exception);
        }
    }

    private void sendAsync(String phone, String key, String code) {
        try {
            // 排队过久或验证码已更新时，不再发送过期任务。
            if (!code.equals(redisTemplate.opsForValue().get(key))) {
                return;
            }
            smsSender.sendVerificationCode(phone, code);
        } catch (RuntimeException exception) {
            // 不记录异常原文，避免供应商异常携带手机号、验证码或鉴权信息。
            log.warn("异步短信发送失败，errorType={}", exception.getClass().getSimpleName());
            removeOwnCode(key, code);
        }
    }

    private void removeOwnCode(String key, String code) {
        try {
            redisTemplate.execute(REMOVE_OWN_CODE, List.of(key), code);
        } catch (RuntimeException exception) {
            log.error("验证码缓存清理失败，等待自动过期，errorType={}", exception.getClass().getSimpleName());
        }
    }
}
