package com.rednote.rednoteauth.domain.dataobject;

import lombok.Data;

/** 角色权限联查结果；无权限的有效角色仍返回一行，permissionKey 为 null。 */
@Data
public class RolePermissionDO {
    private String roleKey;
    private String permissionKey;
}
