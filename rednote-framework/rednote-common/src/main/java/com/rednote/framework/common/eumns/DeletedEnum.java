package com.rednote.framework.common.eumns;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 逻辑删除标记，对应数据库 BIT 字段：0 未删除，1 已删除。 */
@Getter
@RequiredArgsConstructor
public enum DeletedEnum {
    NO(false),
    YES(true);

    private final Boolean value;
}
