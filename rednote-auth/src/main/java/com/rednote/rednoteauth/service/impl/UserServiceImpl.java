package com.rednote.rednoteauth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.rednote.framework.common.eumns.DeletedEnum;
import com.rednote.framework.common.eumns.StatusEnum;
import com.rednote.framework.common.exception.BizException;
import com.rednote.framework.common.util.JsonUtils;
import com.rednote.rednoteauth.constant.RedisKeyConstants;
import com.rednote.rednoteauth.constant.RoleConstants;
import com.rednote.rednoteauth.domain.dataobject.UserDO;
import com.rednote.rednoteauth.domain.mapper.UserDOMapper;
import com.rednote.rednoteauth.enums.LoginTypeEnum;
import com.rednote.rednoteauth.enums.ResponseCodeEnum;
import com.rednote.rednoteauth.model.vo.user.UserLoginReqVO;
import com.rednote.rednoteauth.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserDOMapper userDOMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate registrationTransaction;

    public UserServiceImpl(UserDOMapper userDOMapper, StringRedisTemplate redisTemplate,
                           PlatformTransactionManager transactionManager) {
        this.userDOMapper = userDOMapper;
        this.redisTemplate = redisTemplate;
        this.registrationTransaction = new TransactionTemplate(transactionManager);
        // 每次注册尝试独立提交，确保返回后可以安全写入 Redis 登录态。
        this.registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 首次初始化为 10000 后递增；Lua 保证多实例并发初始化/取号的原子性。
    // 在 Redis 内限制长度，避免超过 t_user.rednote_id 的 VARCHAR(15)。
    private static final DefaultRedisScript<Long> NEXT_REDNOTE_ID = new DefaultRedisScript<>(
            "redis.call('setnx', KEYS[1], '10000'); "
                    + "local value = redis.call('get', KEYS[1]); "
                    + "if not string.match(value, '^%d+$') or string.len(value) > 15 "
                    + "or tonumber(value) < 10000 or tonumber(value) >= 999999999999999 then "
                    + "return redis.error_reply('invalid rednote id generator value') end; "
                    + "return redis.call('incr', KEYS[1]);", Long.class);

    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    @Override
    public String loginAndRegister(UserLoginReqVO request) {
        UserDO user;
        if (Integer.valueOf(LoginTypeEnum.VERIFICATION_CODE.getType()).equals(request.getType())) {
            Long consumed = redisTemplate.execute(CONSUME_CODE,
                    List.of(RedisKeyConstants.buildVerificationCodeKey(request.getPhone())), request.getCode());
            if (!Long.valueOf(1).equals(consumed)) {
                throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
            }
            user = userDOMapper.selectByPhone(request.getPhone());
            if (user == null) {
                user = register(request.getPhone());
            }
        } else if (Integer.valueOf(LoginTypeEnum.PASSWORD.getType()).equals(request.getType())) {
            user = userDOMapper.selectByPhone(request.getPhone());
            if (user == null || !passwordMatches(request.getPassword(), user.getPassword())) {
                throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
            }
        } else {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        if (DeletedEnum.YES.getValue().equals(user.getIsDeleted())
                || !StatusEnum.ENABLE.getValue().equals(user.getStatus())) {
            throw new BizException(ResponseCodeEnum.USER_DISABLED);
        }
        // 注册已提交但缓存写入失败时，下次登录可补齐；不覆盖已有角色配置。
        redisTemplate.opsForValue().setIfAbsent(RedisKeyConstants.buildUserRolesKey(user.getId()),
                JsonUtils.toJsonString(List.of(RoleConstants.COMMON_USER)));
        // INSERT 已提交后再签发 Token，避免数据库回滚后留下有效登录态。
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    private UserDO register(String phone) {
        for (int attempt = 0; attempt < 3; attempt++) {
            LocalDateTime now = LocalDateTime.now();
            UserDO user = UserDO.builder()
                    .phone(phone)
                    .rednoteId(nextRednoteId())
                    .nickname("用户" + RandomUtil.randomString(8))
                    .status(StatusEnum.ENABLE.getValue()).sex(0).isDeleted(DeletedEnum.NO.getValue())
                    .createTime(now).updateTime(now)
                    .build();
            try {
                return registrationTransaction.execute(status -> {
                    int inserted = userDOMapper.insertSelective(user);
                    // 校验也在事务内，主键回填失败时回滚插入。
                    if (inserted != 1 || user.getId() == null) {
                        throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
                    }
                    return user;
                });
            } catch (DuplicateKeyException exception) {
                // execute 抛出异常时本次事务已回滚，后续查询和重试不在失败事务中。
                // 手机号唯一索引防止重复注册；编号冲突则取下一个编号重试。
                UserDO existing = userDOMapper.selectByPhone(phone);
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
    }

    private String nextRednoteId() {
        Long id = redisTemplate.execute(NEXT_REDNOTE_ID, List.of(RedisKeyConstants.REDNOTE_ID_GENERATOR_KEY));
        if (id == null) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
        return id.toString();
    }

    private boolean passwordMatches(String password, String hash) {
        if (password == null || password.isBlank() || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
