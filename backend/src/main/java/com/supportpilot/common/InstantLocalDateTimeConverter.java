package com.supportpilot.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 所有 Instant 字段按东八区本地时间落库（DATETIME，秒精度），
 * 保证库中存储的就是 yyyy-MM-dd HH:mm:ss 形式的本地时间，而不是 UTC 时间点。
 */
@Converter(autoApply = true)
public class InstantLocalDateTimeConverter implements AttributeConverter<Instant, LocalDateTime> {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public LocalDateTime convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : LocalDateTime.ofInstant(attribute, ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    public Instant convertToEntityAttribute(LocalDateTime dbData) {
        return dbData == null ? null : dbData.atZone(ZONE).toInstant();
    }
}
