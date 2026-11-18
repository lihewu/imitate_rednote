package com.rednote.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rednote.gateway.constant.RedisKeyConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private StpInterfaceImpl stpInterface;

    @BeforeEach
    void setUp() {
        stpInterface = new StpInterfaceImpl(stringRedisTemplate, new ObjectMapper());
    }

    @Test
    void getRoleListReturnsParsedRoles() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRolesKey(1001L)))
                .thenReturn("[\"common_user\",\"admin\"]");

        List<String> roles = stpInterface.getRoleList(1001L, "login");

        assertThat(roles).containsExactly("common_user", "admin");
    }

    @Test
    void getRoleListReturnsEmptyWhenMissingOrNullLoginId() {
        assertThat(stpInterface.getRoleList(null, "login")).isEmpty();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRolesKey(1002L))).thenReturn(null);

        assertThat(stpInterface.getRoleList(1002L, "login")).isEmpty();
        verifyNoInteractions(hashOperations);
    }

    @Test
    void getPermissionListAggregatesAndDeduplicatesAcrossRoles() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRolesKey(1001L)))
                .thenReturn("[\"common_user\",\"reviewer\"]");
        when(hashOperations.multiGet(eq(RedisKeyConstants.ROLE_PERMISSIONS_KEY), anyCollection()))
                .thenReturn(Arrays.asList(
                        "[\"app:note:publish\",\"app:note:comment\"]",
                        null,
                        "[\"app:note:comment\",\"app:note:review\"]"
                ));

        List<String> permissions = stpInterface.getPermissionList(1001L, "login");

        assertThat(permissions).containsExactly(
                "app:note:publish",
                "app:note:comment",
                "app:note:review"
        );
    }

    @Test
    void getPermissionListReturnsEmptyWhenUserHasNoRoles() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRolesKey(1003L))).thenReturn("");

        assertThat(stpInterface.getPermissionList(1003L, "login")).isEmpty();
        verifyNoInteractions(hashOperations);
    }

    @Test
    void getRoleListThrowsWhenRedisJsonIsInvalid() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyConstants.buildUserRolesKey(1004L))).thenReturn("{invalid");

        assertThatThrownBy(() -> stpInterface.getRoleList(1004L, "login"))
                .isInstanceOf(IllegalStateException.class);
    }
}
