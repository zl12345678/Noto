package com.noto.zhihui.common.util;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class IcsCalendarBuilder {

    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private IcsCalendarBuilder() {
    }

    public record IcsEvent(
            String uid,
            String summary,
            String description,
            LocalDateTime start,
            LocalDateTime end
    ) {}

    public static String build(String calendarName, List<IcsEvent> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("BEGIN:VCALENDAR\r\n");
        builder.append("VERSION:2.0\r\n");
        builder.append("PRODID:-//Noto Zhihui//Todo Export//ZH\r\n");
        builder.append("CALSCALE:GREGORIAN\r\n");
        builder.append("METHOD:PUBLISH\r\n");
        builder.append("X-WR-CALNAME:").append(escape(calendarName)).append("\r\n");
        if (events == null || events.isEmpty()) {
            builder.append("END:VCALENDAR\r\n");
            return builder.toString();
        }
        for (IcsEvent event : events) {
            if (event.start() == null) {
                continue;
            }
            LocalDateTime end = event.end() != null ? event.end() : event.start().plusHours(1);
            builder.append("BEGIN:VEVENT\r\n");
            builder.append("UID:").append(escape(event.uid())).append("\r\n");
            builder.append("DTSTAMP:").append(LocalDateTime.now().format(ICS_DT)).append("\r\n");
            builder.append("DTSTART:").append(event.start().format(ICS_DT)).append("\r\n");
            builder.append("DTEND:").append(end.format(ICS_DT)).append("\r\n");
            builder.append("SUMMARY:").append(escape(event.summary())).append("\r\n");
            if (StringUtils.hasText(event.description())) {
                builder.append("DESCRIPTION:").append(escape(event.description())).append("\r\n");
            }
            builder.append("END:VEVENT\r\n");
        }
        builder.append("END:VCALENDAR\r\n");
        return builder.toString();
    }

    public static String escape(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }
}
