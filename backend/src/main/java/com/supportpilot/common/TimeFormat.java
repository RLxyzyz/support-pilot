package com.supportpilot.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 统一时间输出格式：yyyy-MM-dd HH:mm:ss（东八区，秒精度）。
 * 所有对外展示 / 落库快照的时间字符串都应走这里，避免出现 ISO（带 T/Z）或只到分钟的写法。
 */
public final class TimeFormat {

    public static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private TimeFormat() {
    }

    public static String format(Instant instant) {
        return instant == null ? null : FMT.format(instant);
    }
}
