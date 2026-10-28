package com.rednote.rednoteauth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.session.SaSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Redis 验证自动装配、会话序列化和 TTL；不加载业务组件或 MySQL。 */
@SpringBootTest(classes = SaTokenRedisTests.TestApplication.class)
@ActiveProfiles("dev")
class SaTokenRedisTests {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class,
            excludeName = "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure")
    static class TestApplication {
    }

    @Autowired
    private SaTokenDao dao;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void autoConfiguresRedisDaoAndPreservesTokenExpirationOnUpdate() {
        assertThat(dao).isInstanceOf(SaTokenDaoForRedisTemplate.class);
        assertThat(SaManager.getSaTokenDao()).isSameAs(dao);
        String key = "rednote:test:satoken:token:" + UUID.randomUUID();
        try {
            dao.set(key, "10001", 60);
            assertThat(redis.opsForValue().get(key)).isEqualTo("10001");
            dao.updateTimeout(key, 30);
            dao.update(key, "10002");
            assertThat(dao.get(key)).isEqualTo("10002");
            assertThat(dao.getTimeout(key)).isBetween(1L, 30L);
            dao.delete(key);
            assertThat(dao.get(key)).isNull();
        } finally {
            dao.delete(key);
        }
    }

    @Test
    void roundTripsSessionAndPreservesExpirationOnUpdate() {
        String key = "rednote:test:satoken:session:" + UUID.randomUUID();
        try {
            SaSession session = new SaSession(key);
            session.set("username", "zhang");
            dao.setSession(session, 60);
            assertThat(redis.hasKey(key)).isTrue();
            SaSession restored = dao.getSession(key);
            assertThat(restored).isNotSameAs(session);
            assertThat(restored.get("username")).isEqualTo("zhang");
            dao.updateSessionTimeout(key, 30);
            restored.set("username", "updated");
            dao.updateSession(restored);
            assertThat(dao.getSession(key).get("username")).isEqualTo("updated");
            assertThat(dao.getSessionTimeout(key)).isBetween(1L, 30L);
            dao.deleteSession(key);
            assertThat(dao.getSession(key)).isNull();
        } finally {
            dao.deleteSession(key);
        }
    }
}
