package com.rednote.rednoteauth;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rednote.rednoteauth.constant.RedisKeyConstants;
import com.rednote.rednoteauth.constant.RoleConstants;
import com.rednote.rednoteauth.domain.dataobject.UserDO;
import com.rednote.rednoteauth.domain.mapper.UserDOMapper;
import com.rednote.rednoteauth.model.vo.user.UserLoginReqVO;
import com.rednote.rednoteauth.service.impl.UserServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 连接本地 MySQL 和 Redis，使用随机手机号并清理自己创建的记录；不会发送短信。 */
@SpringBootTest(properties = "sms.aliyun.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserLoginIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserDOMapper users;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactionManager;
    private String phone;
    private Long ownedUserId;

    @BeforeEach
    void prepare() {
        do {
            phone = "18" + ThreadLocalRandom.current().nextLong(100000000L, 1000000000L);
        } while (users.selectByPhone(phone) != null);
    }

    @AfterEach
    void cleanup() {
        redis.delete(RedisKeyConstants.buildVerificationCodeKey(phone));
        UserDO user = users.selectByPhone(phone);
        if (user != null) {
            StpUtil.logout(user.getId());
            redis.delete(RedisKeyConstants.buildUserRolesKey(user.getId()));
            users.deleteByPrimaryKey(user.getId());
        }
    }

    private void code() {
        redis.opsForValue().set(RedisKeyConstants.buildVerificationCodeKey(phone), "012345", Duration.ofMinutes(3));
    }

    private JsonNode login(Map<String, Object> body, int expectedStatus) throws Exception {
        String response = mvc.perform(post("/user/login").contentType("application/json")
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(response);
    }

    private UserDO existing(String password) {
        LocalDateTime now = LocalDateTime.now();
        UserDO user = UserDO.builder().phone(phone).rednoteId("test" + phone).nickname("登录测试")
                .password(password).status(0).isDeleted(false).createTime(now).updateTime(now).build();
        users.insertSelective(user);
        ownedUserId = user.getId();
        return user;
    }

    @Test
    void registrationRollsBackWhenFailureOccursAfterInsert() {
        code();
        UserDOMapper failingMapper = mock(UserDOMapper.class, delegatesTo(users));
        Long[] insertedId = new Long[1];
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            UserDO user = invocation.getArgument(0);
            users.insertSelective(user);
            insertedId[0] = user.getId();
            assertThat(insertedId[0]).isNotNull();
            throw new IllegalStateException("模拟插入后失败");
        }).when(failingMapper).insertSelective(any(UserDO.class));

        UserLoginReqVO request = new UserLoginReqVO();
        request.setPhone(phone);
        request.setCode("012345");
        request.setType(1);
        UserServiceImpl service = new UserServiceImpl(failingMapper, redis, transactionManager);
        assertThatThrownBy(() -> service.loginAndRegister(request))
                .isInstanceOf(IllegalStateException.class).hasMessage("模拟插入后失败");

        assertThat(users.selectByPhone(phone)).isNull();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(redis.hasKey(RedisKeyConstants.buildUserRolesKey(insertedId[0]))).isFalse();
        assertThat(StpUtil.getSessionByLoginId(insertedId[0], false)).isNull();
        // Redis 不参与 MySQL 事务：验证码仍已消费，编号不回退。
        assertThat(redis.hasKey(RedisKeyConstants.buildVerificationCodeKey(phone))).isFalse();
    }

    @Test
    void registersLogsInAndRejectsCodeReplay() throws Exception {
        code();
        Map<String, Object> request = Map.of("phone", phone, "code", "012345", "type", 1);
        JsonNode response = login(request, 200);
        assertThat(response.get("success").asBoolean()).isTrue();
        String token = response.get("data").asText();
        UserDO user = users.selectByPhone(phone);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getPassword()).isNull();
        assertThat(Long.parseLong(user.getRednoteId())).isGreaterThan(10000L);
        assertThat(redis.opsForValue().get(RedisKeyConstants.REDNOTE_ID_GENERATOR_KEY)).isEqualTo(user.getRednoteId());
        assertThat(redis.getExpire(RedisKeyConstants.REDNOTE_ID_GENERATOR_KEY)).isEqualTo(-1L);
        String rolesKey = RedisKeyConstants.buildUserRolesKey(user.getId());
        assertThat(json.readTree(redis.opsForValue().get(rolesKey)).get(0).asText()).isEqualTo(RoleConstants.COMMON_USER);
        assertThat(redis.opsForValue().get("satoken:login:token:" + token)).isEqualTo(user.getId().toString());
        assertThat(redis.getExpire("satoken:login:token:" + token)).isPositive();
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20002");
        // 再次登录不重新分配编号，也不覆盖已有角色。
        redis.opsForValue().set(rolesKey, "[\"custom_role\"]");
        code();
        assertThat(login(request, 200).get("success").asBoolean()).isTrue();
        assertThat(users.selectByPhone(phone).getId()).isEqualTo(user.getId());
        assertThat(redis.opsForValue().get(RedisKeyConstants.REDNOTE_ID_GENERATOR_KEY)).isEqualTo(user.getRednoteId());
        assertThat(redis.opsForValue().get(rolesKey)).isEqualTo("[\"custom_role\"]");
        redis.delete(rolesKey);
        code();
        assertThat(login(request, 200).get("success").asBoolean()).isTrue();
        assertThat(json.readTree(redis.opsForValue().get(rolesKey)).get(0).asText()).isEqualTo(RoleConstants.COMMON_USER);
    }

    @Test
    void wrongOrExpiredCodeDoesNotRegister() throws Exception {
        code();
        Map<String, Object> request = Map.of("phone", phone, "code", "999999", "type", 1);
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20002");
        assertThat(redis.opsForValue().get(RedisKeyConstants.buildVerificationCodeKey(phone))).isEqualTo("012345");
        redis.delete(RedisKeyConstants.buildVerificationCodeKey(phone));
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20002");
        assertThat(users.selectByPhone(phone)).isNull();
    }

    @Test
    void passwordLoginRequiresExistingAccountAndMatchingHash() throws Exception {
        Map<String, Object> request = Map.of("phone", phone, "password", "correct-password", "type", 2);
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20003");
        assertThat(users.selectByPhone(phone)).isNull();
        existing(BCrypt.hashpw("correct-password"));
        assertThat(login(Map.of("phone", phone, "password", "wrong", "type", 2), 200)
                .get("errorCode").asText()).isEqualTo("AUTH-20003");
        JsonNode response = login(request, 200);
        assertThat(response.get("success").asBoolean()).isTrue();
        assertThat(StpUtil.getLoginIdByToken(response.get("data").asText()).toString()).isEqualTo(ownedUserId.toString());
    }

    @Test
    void unsetOrInvalidPasswordIsRejected() throws Exception {
        UserDO user = existing(null);
        Map<String, Object> request = Map.of("phone", phone, "password", "plaintext", "type", 2);
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20003");
        user.setPassword("plaintext");
        users.updateByPrimaryKeySelective(user);
        assertThat(login(request, 200).get("errorCode").asText()).isEqualTo("AUTH-20003");
    }

    @Test
    void disabledAndDeletedUsersCannotLogin() throws Exception {
        UserDO user = existing(BCrypt.hashpw("password"));
        user.setStatus(1);
        users.updateByPrimaryKeySelective(user);
        code();
        assertThat(login(Map.of("phone", phone, "code", "012345", "type", 1), 200)
                .get("errorCode").asText()).isEqualTo("AUTH-20004");
        user.setStatus(0);
        user.setIsDeleted(true);
        users.updateByPrimaryKeySelective(user);
        assertThat(login(Map.of("phone", phone, "password", "password", "type", 2), 200)
                .get("errorCode").asText()).isEqualTo("AUTH-20004");
    }

    @Test
    void validatesTypePhoneAndConditionalCredentials() throws Exception {
        code();
        for (Map<String, Object> request : java.util.List.<Map<String, Object>>of(
                Map.of("phone", phone), Map.of("phone", phone, "type", 3),
                Map.of("phone", "bad", "type", 1, "code", "012345"),
                Map.of("phone", phone, "type", 1), Map.of("phone", phone, "type", 1, "code", "123"),
                Map.of("phone", phone, "type", 2), Map.of("phone", phone, "type", 2, "password", " "),
                Map.of("phone", phone, "type", 2, "password", "a".repeat(73)))) {
            JsonNode response = login(request, 400);
            assertThat(response.get("success").asBoolean()).isFalse();
            assertThat(response.get("errorCode").asText()).isEqualTo("10001");
            assertThat(response.get("message").asText()).isNotBlank();
            assertThat(response.get("data").isNull()).isTrue();
        }
        assertThat(login(Map.of("phone", phone, "type", 2), 400).get("message").asText())
                .isEqualTo("密码不能为空");
        assertThat(redis.opsForValue().get(RedisKeyConstants.buildVerificationCodeKey(phone))).isEqualTo("012345");
        assertThat(users.selectByPhone(phone)).isNull();
    }

    @Test
    void directServiceCallValidatesBeforeAccessingStorage() {
        UserDOMapper mapper = mock(UserDOMapper.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        UserServiceImpl service = new UserServiceImpl(mapper, template, transactionManager);
        assertThatThrownBy(() -> service.loginAndRegister(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("登录参数不能为空");
        UserLoginReqVO request = new UserLoginReqVO();
        request.setPhone(phone);
        request.setType(1);
        request.setCode("not-a-code");
        assertThatThrownBy(() -> service.loginAndRegister(request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("验证码必须为六位数字");
        verifyNoInteractions(mapper, template);
    }
}
