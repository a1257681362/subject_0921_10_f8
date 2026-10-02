package com.evops.absence.common;

import com.evops.common.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 发生时间解析：支持 yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd HH:mm / yyyy-MM-dd。
 */
public final class TimeFormats {

    private TimeFormats() {
    }

    public static LocalDateTime parse(String text, LocalDateTime fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        String value = text.trim();
        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException("OCCURRED_AT_INVALID",
                    "时间格式不正确（应为 yyyy-MM-dd HH:mm(:ss) 或 yyyy-MM-dd）：" + text);
        }
    }

    public static void assertNotFuture(LocalDateTime occurredAt, LocalDateTime now) {
        if (occurredAt.isAfter(now)) {
            throw new BusinessException("OCCURRED_AT_FUTURE", "时间不能晚于当前时间：" + occurredAt);
        }
    }
}
