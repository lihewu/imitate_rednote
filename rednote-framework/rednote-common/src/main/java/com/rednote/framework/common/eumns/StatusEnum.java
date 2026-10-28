package com.rednote.framework.common.eumns;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 开启/禁用状态，与用户表约定一致：0 正常（开启），1 禁用。 */
@Getter
@RequiredArgsConstructor
public enum StatusEnum {
    ENABLE(0),
    DISABLE(1);

    private final Integer value;
}
