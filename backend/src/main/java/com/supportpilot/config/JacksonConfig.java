package com.supportpilot.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.supportpilot.common.TimeFormat;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;

/**
 * 全局 JSON 时间格式：Instant 统一序列化为 yyyy-MM-dd HH:mm:ss（东八区）。
 * 兜底所有直接暴露时间字段的接口，避免出现 2026-11-23T11:16:33Z 这类 ISO 格式。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timeFormatCustomizer() {
        return builder -> builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(TimeFormat.format(value));
            }
        });
    }
}
