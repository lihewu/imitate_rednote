package com.rednote.rednoteauth.domain.mapper;

import com.rednote.rednoteauth.domain.dataobject.RolePermissionDO;
import java.util.List;

public interface RolePermissionMapper {
    List<RolePermissionDO> selectEnabledRolePermissions();
}
