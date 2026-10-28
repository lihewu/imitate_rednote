package com.rednote.rednoteauth.domain.dataobject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** 用户表 t_user；日期、时间与数据库字段保持一致。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDO {
    private Long id;
    private String rednoteId;
    @ToString.Exclude
    private String password;
    private String nickname;
    private String avatar;
    private LocalDate birthday;
    private String backgroundImg;
    private String phone;
    private Integer sex;
    private Integer status;
    private String introduction;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isDeleted;
}
