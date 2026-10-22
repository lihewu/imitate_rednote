package com.rednote.rednoteauth;

import com.rednote.rednoteauth.config.RedisTemplateConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 连接 dev 配置的真实 Redis；只加载 Redis 组件，不依赖 MySQL。 */
@DataRedisTest(properties = "spring.data.redis.repositories.enabled=false")
@ActiveProfiles("dev")
@ContextConfiguration(classes = RedisTemplateConfig.class)
class RedisTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void writesReadsAndDeletesValueWithExpiration() {
        String key = "rednote:test:value:" + UUID.randomUUID();
        try {
            redisTemplate.opsForValue().set(key, "你好，Redis！", Duration.ofMinutes(1));

            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("你好，Redis！");
            assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
            assertThat(redisTemplate.delete(key)).isTrue();
            assertThat(redisTemplate.hasKey(key)).isFalse();
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void roundTripsJsonValuesAndHashEntries() {
        String key = "rednote:test:json:" + UUID.randomUUID();
        String hashKey = "rednote:test:hash:" + UUID.randomUUID();
        Map<String, Object> user = new HashMap<>();
        user.put("id", 10001);
        user.put("username", "zhang");
        try {
            redisTemplate.opsForValue().set(key, user, Duration.ofMinutes(1));
            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(user);

            redisTemplate.opsForHash().put(hashKey, "user", user);
            assertThat(redisTemplate.expire(hashKey, Duration.ofMinutes(1))).isTrue();
            assertThat(redisTemplate.opsForHash().get(hashKey, "user")).isEqualTo(user);
            assertThat(redisTemplate.opsForHash().delete(hashKey, "user")).isEqualTo(1L);
            assertThat(redisTemplate.opsForHash().hasKey(hashKey, "user")).isFalse();
        } finally {
            redisTemplate.delete(key);
            redisTemplate.delete(hashKey);
        }
    }
}
