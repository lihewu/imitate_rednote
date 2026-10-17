package com.quanxiaoha.framework.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * JSON 工具类，提供对象序列化和反序列化。
 * Java 时间类型使用 ISO-8601 字符串，反序列化时忽略未知字段。
 */
public final class JsonUtils {

    // 初始化后不再修改配置，可安全地在多个线程间复用。
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtils() {
    }

    /**
     * 将对象转换为 JSON 字符串；null 转换为字符串 "null"。
     *
     * @throws IllegalArgumentException 对象无法序列化时抛出，保留原始异常
     */
    public static String toJsonString(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("对象转换为 JSON 失败", e);
        }
    }

    /**
     * 将 JSON 字符串转换为指定类型；输入字符串 "null" 时返回 null。
     *
     * @throws IllegalArgumentException JSON 为 null、格式错误或与目标类型不匹配时抛出
     */
    public static <T> T parseObject(String json, Class<T> type) {
        Objects.requireNonNull(type, "type");
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转换为对象失败", e);
        }
    }

    /**
     * 将 JSON 字符串转换为泛型对象，如 new TypeReference&lt;List&lt;User&gt;&gt;() {}。
     *
     * @throws IllegalArgumentException JSON 为 null、格式错误或与目标类型不匹配时抛出
     */
    public static <T> T parseObject(String json, TypeReference<T> type) {
        Objects.requireNonNull(type, "type");
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转换为泛型对象失败", e);
        }
    }
}
