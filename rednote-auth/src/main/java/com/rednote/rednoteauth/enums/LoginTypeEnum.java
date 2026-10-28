package com.rednote.rednoteauth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LoginTypeEnum {
    VERIFICATION_CODE(1),
    PASSWORD(2);

    private final int type;
}
