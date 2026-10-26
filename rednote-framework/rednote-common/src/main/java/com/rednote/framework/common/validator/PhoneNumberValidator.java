package com.rednote.framework.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** 校验以 1 开头、第二位为 3–9 的 11 位手机号。 */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^1[3-9][0-9]{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 与标准格式约束保持一致，null 交由非空约束处理。
        return value == null || PHONE_NUMBER_PATTERN.matcher(value).matches();
    }
}
