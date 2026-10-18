package com.rednote.rednoteauth;

import com.rednote.rednoteauth.domain.dataobject.UserDO;
import com.rednote.rednoteauth.domain.mapper.UserDOMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测试方法使用 @Transactional，执行完自动回滚，不会污染数据库
 */
@SpringBootTest
@Transactional
class RednoteAuthApplicationTests {

    @Autowired
    private UserDOMapper userDOMapper;

    private UserDO buildUser() {
        return UserDO.builder()
                .username("测试用户")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    @Test
    void contextLoads() {
        assertNotNull(userDOMapper);
    }

    /** 测试插入并按主键查询 */
    @Test
    void testInsertAndSelect() {
        UserDO user = buildUser();
        userDOMapper.insert(user);
        assertNotNull(user.getId());

        UserDO result = userDOMapper.selectByPrimaryKey(user.getId());
        assertNotNull(result);
        assertEquals("测试用户", result.getUsername());
    }

    /** 测试选择性更新 */
    @Test
    void testUpdate() {
        UserDO user = buildUser();
        userDOMapper.insert(user);

        UserDO update = UserDO.builder().id(user.getId()).username("新用户名").build();
        userDOMapper.updateByPrimaryKeySelective(update);

        UserDO result = userDOMapper.selectByPrimaryKey(user.getId());
        assertEquals("新用户名", result.getUsername());
        assertNotNull(result.getCreateTime());
    }

    /** 测试删除 */
    @Test
    void testDelete() {
        UserDO user = buildUser();
        userDOMapper.insert(user);

        userDOMapper.deleteByPrimaryKey(user.getId());
        assertNull(userDOMapper.selectByPrimaryKey(user.getId()));
    }

}
