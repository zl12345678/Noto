package com.noto.zhihui.common.util;

import com.noto.zhihui.common.constants.TodoHorizon;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownTodoExtractor {

    private static final Pattern CHECKBOX = Pattern.compile("(?m)^\\s*[-*+]\\s*\\[\\s*([xX ])\\s*\\]\\s+(.+)$");
    private static final Pattern TODO_LINE = Pattern.compile("(?m)^\\s*(?:TODO|待办)[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private MarkdownTodoExtractor() {
    }

    public static List<ExtractedTodoItem> extract(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<ExtractedTodoItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Matcher checkboxMatcher = CHECKBOX.matcher(content);
        while (checkboxMatcher.find()) {
            addItem(items, seen, checkboxMatcher.group(2), "x".equalsIgnoreCase(checkboxMatcher.group(1).trim()));
        }

        Matcher todoMatcher = TODO_LINE.matcher(content);
        while (todoMatcher.find()) {
            addItem(items, seen, todoMatcher.group(1), false);
        }

        return items;
    }

    private static void addItem(List<ExtractedTodoItem> items, Set<String> seen, String rawTitle, boolean completed) {
        String title = sanitizeTitle(rawTitle);
        if (title.isBlank()) {
            return;
        }
        String key = title.toLowerCase();
        if (!seen.add(key)) {
            return;
        }
        items.add(new ExtractedTodoItem(title, completed));
    }

    private static String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        return rawTitle
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("[*_`]", "")
                .trim();
    }

    public record ExtractedTodoItem(
            String title,
            boolean completed,
            Integer priority,
            String horizon,
            LocalDateTime dueAt
    ) {

        public ExtractedTodoItem(String title, boolean completed) {
            this(title, completed, 2, TodoHorizon.ACTION, null);
        }

        public int resolvedPriority() {
            if (priority == null || priority < 1 || priority > 3) {
                return 2;
            }
            return priority;
        }

        public String resolvedHorizon() {
            return TodoHorizon.normalize(horizon);
        }

        public LocalDateTime resolvedDueAt() {
            return ExtractedTodoDueHelper.resolveDueAt(dueAt, resolvedHorizon(), resolvedPriority());
        }
    }
}
