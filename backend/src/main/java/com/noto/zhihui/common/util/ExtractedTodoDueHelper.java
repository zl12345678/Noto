package com.noto.zhihui.common.util;

import com.noto.zhihui.common.constants.TodoHorizon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class ExtractedTodoDueHelper {

    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(18, 0);

    private ExtractedTodoDueHelper() {
    }

    public static LocalDateTime parseDueAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if ("null".equalsIgnoreCase(normalized) || "none".equalsIgnoreCase(normalized)) {
            return null;
        }
        normalized = normalized.replace(" ", "T");
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return normalizeExtractedDueAt(LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return normalizeExtractedDueAt(
                    LocalDate.parse(normalized.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE)
                            .atTime(DEFAULT_DUE_TIME)
            );
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 提取预览/入库时使用：仅规范化，不臆造默认截止日。
     */
    public static LocalDateTime normalizeExtractedDueAt(LocalDateTime dueAt) {
        if (dueAt == null) {
            return null;
        }
        if (dueAt.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return dueAt.with(DEFAULT_DUE_TIME);
        }
        return dueAt;
    }

    public static LocalDateTime inferDefaultDueAt(String horizon, int priority) {
        LocalDate today = LocalDate.now();
        if (TodoHorizon.LONG_TERM.equals(TodoHorizon.normalize(horizon))) {
            return LocalDateTime.of(today.plusMonths(3), DEFAULT_DUE_TIME);
        }
        return switch (priority) {
            case 1 -> LocalDateTime.of(today, DEFAULT_DUE_TIME);
            case 3 -> LocalDateTime.of(today.plusDays(3), DEFAULT_DUE_TIME);
            default -> LocalDateTime.of(today.plusDays(1), DEFAULT_DUE_TIME);
        };
    }

    public static LocalDateTime resolveDueAt(LocalDateTime dueAt, String horizon, int priority) {
        return normalizeExtractedDueAt(dueAt);
    }
}
