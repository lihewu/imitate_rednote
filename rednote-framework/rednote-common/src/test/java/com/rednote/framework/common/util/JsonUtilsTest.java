package com.rednote.framework.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rednote.framework.common.response.Response;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonUtilsTest {

    @Before
    public void initializeMapper() {
        // 公共模块独立测试时，由测试提供 ObjectMapper，生产配置由服务统一管理。
        JsonUtils.init(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    public void serializesAndRestoresTextWithSpecialCharacters() {
        String text = "业务日志：\"登录\"\n下一行\\结束";
        String json = JsonUtils.toJsonString(Map.of("message", text));
        Map<String, String> restored = JsonUtils.parseObject(json, new TypeReference<>() {});

        assertEquals(text, restored.get("message"));
    }

    @Test
    public void restoresNestedGenericResponse() {
        Response<List<Payload>> response = JsonUtils.parseObject(
                "{\"success\":true,\"data\":[{\"name\":\"rednote\"}]}",
                new TypeReference<Response<List<Payload>>>() {});

        assertTrue(response.isSuccess());
        assertEquals("rednote", response.getData().get(0).name);
    }

    @Test
    public void supportsJavaTimeAsIsoStrings() {
        Payload payload = new Payload();
        payload.name = "操作日志";
        payload.date = LocalDate.of(2026, 10, 17);
        payload.time = LocalDateTime.of(2026, 10, 17, 12, 30, 45);

        String json = JsonUtils.toJsonString(payload);
        Map<String, Object> values = JsonUtils.parseObject(json, new TypeReference<>() {});
        assertEquals("2026-10-17", values.get("date"));
        assertEquals("2026-10-17T12:30:45", values.get("time"));

        Payload restored = JsonUtils.parseObject(json, Payload.class);
        assertEquals(payload.name, restored.name);
        assertEquals(payload.date, restored.date);
        assertEquals(payload.time, restored.time);
    }

    @Test
    public void ignoresUnknownFields() {
        Payload payload = JsonUtils.parseObject(
                "{\"name\":\"rednote\",\"futureField\":true}", Payload.class);

        assertEquals("rednote", payload.name);
    }

    @Test
    public void handlesJsonNull() {
        assertEquals("null", JsonUtils.toJsonString(null));
        assertNull(JsonUtils.parseObject("null", Payload.class));
        assertNull(JsonUtils.parseObject("null", new TypeReference<List<Payload>>() {}));
    }

    @Test
    public void invalidJsonRetainsCause() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonUtils.parseObject("{", Payload.class));
        assertTrue(exception.getCause() instanceof JsonProcessingException);

        IllegalArgumentException genericException = assertThrows(IllegalArgumentException.class,
                () -> JsonUtils.parseObject("[", new TypeReference<List<Payload>>() {}));
        assertTrue(genericException.getCause() instanceof JsonProcessingException);
    }

    @Test
    public void serializationFailureRetainsCause() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonUtils.toJsonString(new BrokenPayload()));

        assertTrue(exception.getCause() instanceof JsonProcessingException);
    }

    public static class Payload {
        public String name;
        public LocalDate date;
        public LocalDateTime time;
    }

    public static class BrokenPayload {
        public String getValue() {
            throw new IllegalStateException("无法读取属性");
        }
    }
}
