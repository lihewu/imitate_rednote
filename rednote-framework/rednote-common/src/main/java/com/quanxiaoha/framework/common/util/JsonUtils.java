package com.quanxiaoha.framework.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * JSON 工具类，提供对象序列化和反序列化。
 * 复用服务配置的 ObjectMapper，使用前通过 init 完成初始化。
 */
public final class JsonUtils {

    // volatile 保证启动时注入的实例对其他线程可见；工具类不再单独配置 Jackson。
    private static volatile ObjectMapper objectMapper;

    private JsonUtils() {
    }

    /**
     * 在服务启动时传入已完成配置的 ObjectMapper，初始化后不再修改其配置。
     */
    public static void init(ObjectMapper mapper) {
        objectMapper = Objects.requireNonNull(mapper, "mapper");
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            throw new IllegalStateException("JsonUtils 未初始化，请先调用 JsonUtils.init(ObjectMapper)");
        }
        return mapper;
    }

    /**
     * 将对象转换为 JSON 字符串；null 转换为字符串 "null"。
     *
     * @throws IllegalArgumentException 对象无法序列化时抛出，保留原始异常
     */
    public static String toJsonString(Object value) {
        try {
            return getObjectMapper().writeValueAsString(value);
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
            return getObjectMapper().readValue(json, type);
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
            return getObjectMapper().readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转换为泛型对象失败", e);
        }
    }
}
