package com.rednote.rednoteauth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanxiaoha.framework.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JacksonConfig.class);

    @Test
    void dateTimeFieldsRoundTripThroughJson() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            DatePayload payload = new DatePayload(
                    LocalDateTime.of(2026, 10, 22, 12, 34, 56),
                    LocalDate.of(2026, 10, 22),
                    LocalTime.of(12, 34, 56),
                    Date.from(Instant.parse("2026-10-22T04:34:56Z")));

            String json = mapper.writeValueAsString(payload);
            var tree = mapper.readTree(json);
            assertThat(tree.get("dateTime").asText()).isEqualTo("2026-10-22 12:34:56");
            assertThat(tree.get("date").asText()).isEqualTo("2026-10-22");
            assertThat(tree.get("time").asText()).isEqualTo("12:34:56");
            assertThat(tree.get("legacyDate").asText()).isEqualTo("2026-10-22 12:34:56");
            assertThat(mapper.readValue(json, DatePayload.class)).isEqualTo(payload);
            assertThat(JsonUtils.toJsonString(payload)).isEqualTo(json);
            assertThat(JsonUtils.parseObject(json, DatePayload.class)).isEqualTo(payload);
            assertThat(JsonUtils.parseObject("[" + json + "]", new TypeReference<List<DatePayload>>() {}))
                    .containsExactly(payload);
        });
    }

    @Test
    void springJacksonPropertiesStillApply() {
        contextRunner.withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE")
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    NamingPayload payload = new NamingPayload();
                    payload.createTime = LocalDateTime.of(2026, 10, 22, 12, 34, 56);

                    String json = mapper.writeValueAsString(payload);
                    assertThat(mapper.readTree(json).get("create_time").asText())
                            .isEqualTo("2026-10-22 12:34:56");
                    assertThat(JsonUtils.toJsonString(payload)).isEqualTo(json);
                    assertThat(JsonUtils.parseObject(json, NamingPayload.class).createTime)
                            .isEqualTo(payload.createTime);
                });
    }

    record DatePayload(LocalDateTime dateTime, LocalDate date, LocalTime time, Date legacyDate) {
    }

    public static class NamingPayload {
        public LocalDateTime createTime;
    }
}
