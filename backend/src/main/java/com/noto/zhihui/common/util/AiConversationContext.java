package com.noto.zhihui.common.util;

import com.noto.zhihui.vo.ai.AiChatMessageVO;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiConversationContext {

    private static final int MAX_CONTEXT_CHARS = 2400;
    private static final int MAX_RETRIEVAL_QUERY_CHARS = 280;
    private static final Pattern FOLLOW_UP_SIGNAL = Pattern.compile(
            "这|那|它|他|她|上面|刚才|之前|前面|第二个|第三点|继续|还有|同样|详细|展开|再说|什么意思"
    );
    private static final Pattern USER_LINE = Pattern.compile("^用户[：:](.+)$", Pattern.MULTILINE);

    private AiConversationContext() {
    }

    public static String resolveContextBlock(String recentContext, String sessionBlock) {
        if (StringUtils.hasText(recentContext) && StringUtils.hasText(sessionBlock)) {
            return trimToMax(mergeDialogBlocks(recentContext.trim(), sessionBlock.trim()), MAX_CONTEXT_CHARS);
        }
        if (StringUtils.hasText(recentContext)) {
            return trimToMax(recentContext.trim(), MAX_CONTEXT_CHARS);
        }
        if (StringUtils.hasText(sessionBlock)) {
            return trimToMax(sessionBlock.trim(), MAX_CONTEXT_CHARS);
        }
        return "";
    }

    private static String mergeDialogBlocks(String recentContext, String sessionBlock) {
        StringBuilder builder = new StringBuilder();
        appendUniqueLines(builder, sessionBlock);
        appendUniqueLines(builder, recentContext);
        return builder.toString();
    }

    private static void appendUniqueLines(StringBuilder builder, String block) {
        for (String rawLine : block.split("\\R")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String existing = builder.toString();
            if (existing.contains(line)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(line);
        }
    }

    public static String formatDialogBlock(List<AiChatMessageVO> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (AiChatMessageVO message : messages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(message.getRole()) ? "助手" : "用户";
            String line = role + "：" + message.getContent().trim().replaceAll("\\s+", " ");
            if (builder.length() + line.length() + 1 > MAX_CONTEXT_CHARS) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    public static String expandRetrievalQuery(String question, String contextBlock) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String trimmed = question.trim();
        if (!StringUtils.hasText(contextBlock) || !needsContextBoost(trimmed)) {
            return trimmed;
        }
        String topic = extractLastUserTopic(contextBlock);
        if (!StringUtils.hasText(topic)) {
            return trimmed;
        }
        String merged = (topic + " " + trimmed).trim();
        return trimToMax(merged, MAX_RETRIEVAL_QUERY_CHARS);
    }

    public static boolean needsContextBoost(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String trimmed = question.trim();
        if (trimmed.length() <= 18) {
            return true;
        }
        return FOLLOW_UP_SIGNAL.matcher(trimmed).find();
    }

    public static String extractLastUserTopic(String contextBlock) {
        if (!StringUtils.hasText(contextBlock)) {
            return "";
        }
        Matcher matcher = USER_LINE.matcher(contextBlock.trim());
        String last = "";
        while (matcher.find()) {
            last = matcher.group(1).trim();
        }
        return last;
    }

    private static String trimToMax(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }
}
