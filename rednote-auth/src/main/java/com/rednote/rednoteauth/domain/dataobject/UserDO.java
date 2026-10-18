package com.rednote.rednoteauth.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户测试表，对应数据库表 t_user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDO {

    private Long id;

    private String username;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
