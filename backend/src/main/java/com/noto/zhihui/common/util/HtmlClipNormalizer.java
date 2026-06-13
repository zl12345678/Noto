package com.noto.zhihui.common.util;

import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class HtmlClipNormalizer {

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>"
    );
    private static final Pattern BR_OR_P = Pattern.compile("(?i)</?(p|div|br|h[1-6]|li|tr)[^>]*>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private HtmlClipNormalizer() {
    }

    public static String normalize(String content, String contentType) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String type = contentType == null ? "plain" : contentType.trim().toLowerCase();
        return switch (type) {
            case "html" -> htmlToMarkdown(content);
            case "markdown", "md" -> content.trim();
            default -> plainToMarkdown(content);
        };
    }

    private static String htmlToMarkdown(String html) {
        String stripped = SCRIPT_OR_STYLE.matcher(html).replaceAll("");
        stripped = BR_OR_P.matcher(stripped).replaceAll("\n");
        stripped = TAGS.matcher(stripped).replaceAll("");
        stripped = decodeBasicEntities(stripped);
        return collapseBlankLines(stripped.trim());
    }

    private static String plainToMarkdown(String plain) {
        return collapseBlankLines(plain.trim());
    }

    private static String decodeBasicEntities(String text) {
        String decoded = text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        try {
            return URLDecoder.decode(decoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return decoded;
        }
    }

    private static String collapseBlankLines(String text) {
        return text.replaceAll("\\n{3,}", "\n\n");
    }
}
