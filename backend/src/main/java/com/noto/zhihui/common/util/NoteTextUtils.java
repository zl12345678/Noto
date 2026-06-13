package com.noto.zhihui.common.util;

import java.util.Locale;

public final class NoteTextUtils {

    private NoteTextUtils() {
    }

    public static String buildSummary(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String plain = normalize(content);
        if (plain.length() <= maxLength) {
            return plain;
        }
        return plain.substring(0, maxLength) + "...";
    }

    public static String excerpt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = normalize(content);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    public static String highlightSnippet(String content, String keyword, int maxLength) {
        TextMatchRange range = findMatchRange(content, keyword, maxLength);
        return range == null ? excerpt(normalize(content), maxLength) : range.snippet();
    }

    public static TextMatchRange findMatchRange(String content, String keyword, int maxLength) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String resolvedKeyword = resolveKeyword(content, keyword);
        if (resolvedKeyword == null) {
            String plain = normalize(content);
            return new TextMatchRange(0, Math.min(content.length(), maxLength), excerpt(plain, maxLength), "");
        }
        int index = indexOfIgnoreCase(content, resolvedKeyword);
        if (index < 0) {
            String snippet = highlightSnippetNormalized(content, resolvedKeyword, maxLength);
            return new TextMatchRange(null, null, snippet, resolvedKeyword);
        }
        int end = index + resolvedKeyword.length();
        int snippetStart = Math.max(0, index - 30);
        int snippetEnd = Math.min(content.length(), end + 50);
        String snippet = content.substring(snippetStart, snippetEnd).trim();
        if (snippetStart > 0) {
            snippet = "..." + snippet;
        }
        if (snippetEnd < content.length()) {
            snippet = snippet + "...";
        }
        return new TextMatchRange(index, end, snippet, resolvedKeyword);
    }

    private static String highlightSnippetNormalized(String content, String keyword, int maxLength) {
        String normalized = normalize(content);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT).trim();
        int normIndex = normalized.toLowerCase(Locale.ROOT).indexOf(lowerKeyword);
        if (normIndex < 0) {
            return excerpt(normalized, maxLength);
        }
        int start = Math.max(0, normIndex - 30);
        int end = Math.min(normalized.length(), normIndex + lowerKeyword.length() + 50);
        String snippet = normalized.substring(start, end).trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < normalized.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private static String resolveKeyword(String content, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (indexOfIgnoreCase(content, trimmed) >= 0) {
            return trimmed;
        }
        String[] tokens = trimmed.split("[\\s,，。！？；：、.!?;:]+");
        for (String token : tokens) {
            if (token.length() >= 2 && indexOfIgnoreCase(content, token) >= 0) {
                return token;
            }
        }
        return trimmed.length() >= 2 ? trimmed : null;
    }

    private static int indexOfIgnoreCase(String content, String keyword) {
        return content.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT).trim());
    }

    public static String highlightHtml(String content, String keyword) {
        String snippet = highlightSnippet(content, keyword, 120);
        if (keyword == null || keyword.isBlank()) {
            return escapeHtml(snippet);
        }
        String trimmed = keyword.trim();
        int idx = indexOfIgnoreCase(snippet, trimmed);
        if (idx < 0) {
            return escapeHtml(snippet);
        }
        String before = snippet.substring(0, idx);
        String match = snippet.substring(idx, idx + trimmed.length());
        String after = snippet.substring(idx + trimmed.length());
        return escapeHtml(before) + "<em>" + escapeHtml(match) + "</em>" + escapeHtml(after);
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("[*_>`\\[\\]()#]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
