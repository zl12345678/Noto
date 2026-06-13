package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.vo.ai.AiAgentStepVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将只读工具的执行结果整理成用户可读的自然语言回复。
 */
public final class AgentReplyComposer {

    private AgentReplyComposer() {
    }

    public static String compose(String planReply, List<AiAgentStepVO> steps, ObjectMapper objectMapper) {
        List<String> sections = new ArrayList<>();
        if (StringUtils.hasText(planReply)) {
            sections.add(planReply.trim());
        }
        if (steps != null) {
            for (AiAgentStepVO step : steps) {
                if (!"done".equals(step.getStatus()) || !StringUtils.hasText(step.getOutput())) {
                    continue;
                }
                String formatted = formatStepOutput(step.getTool(), step.getOutput(), objectMapper);
                if (StringUtils.hasText(formatted)) {
                    sections.add(formatted);
                }
            }
        }
        if (sections.isEmpty()) {
            return StringUtils.hasText(planReply) ? planReply.trim() : "已处理你的请求。";
        }
        return String.join("\n\n", sections);
    }

    private static String formatStepOutput(String tool, String output, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(output);
            return switch (tool) {
                case "listNotes" -> node.has("catalog") ? node.get("catalog").asText() : null;
                case "listTodos" -> formatTodoList(node);
                case "listReminders" -> formatReminderList(node);
                case "searchNotes" -> formatSearchNotes(node);
                case "searchTodos" -> formatTodoList(node);
                case "listDriveFiles", "listDriveFolders", "listShares", "listTags", "listNoteFolders" ->
                        formatGenericList(node);
                default -> null;
            };
        } catch (Exception ex) {
            return null;
        }
    }

    private static String formatSearchNotes(JsonNode node) {
        if (!node.has("items") || !node.get("items").isArray()) {
            return node.has("count") ? "**找到 " + node.get("count").asInt() + " 篇相关文档**" : null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("**找到 ").append(node.path("count").asInt(0)).append(" 篇相关文档：**\n\n");
        for (JsonNode item : node.get("items")) {
            builder.append("- **")
                    .append(item.path("title").asText("未命名"))
                    .append("**");
            if (item.hasNonNull("snippet") && StringUtils.hasText(item.get("snippet").asText())) {
                builder.append("\n  ").append(item.get("snippet").asText().trim());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String formatTodoList(JsonNode node) {
        if (!node.has("items") || !node.get("items").isArray()) {
            return null;
        }
        int count = node.path("count").asInt(node.get("items").size());
        StringBuilder builder = new StringBuilder();
        builder.append("**共 ").append(count).append(" 条待办：**\n\n");
        for (JsonNode item : node.get("items")) {
            builder.append("- **")
                    .append(item.path("title").asText("未命名"))
                    .append("**");
            List<String> meta = new ArrayList<>();
            if (item.hasNonNull("dueAt")) {
                meta.add("截止 " + item.get("dueAt").asText().replace('T', ' '));
            }
            if (item.hasNonNull("statusLabel")) {
                meta.add(item.get("statusLabel").asText());
            }
            if (!meta.isEmpty()) {
                builder.append(" · ").append(String.join(" · ", meta));
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String formatReminderList(JsonNode node) {
        if (!node.has("items") || !node.get("items").isArray()) {
            return null;
        }
        int count = node.path("count").asInt(node.get("items").size());
        StringBuilder builder = new StringBuilder();
        builder.append("**共 ").append(count).append(" 条提醒：**\n\n");
        for (JsonNode item : node.get("items")) {
            builder.append("- **")
                    .append(item.path("message").asText("提醒"))
                    .append("** · ")
                    .append(item.path("triggerAt").asText("").replace('T', ' '))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static String formatGenericList(JsonNode node) {
        if (!node.has("items") || !node.get("items").isArray() || node.get("items").isEmpty()) {
            return node.has("count") ? "**共 " + node.get("count").asInt() + " 项**" : null;
        }
        int count = node.path("count").asInt(node.get("items").size());
        StringBuilder builder = new StringBuilder();
        builder.append("**共 ").append(count).append(" 项：**\n\n");
        for (JsonNode item : node.get("items")) {
            String label = firstNonBlank(
                    item.path("title").asText(null),
                    item.path("name").asText(null),
                    item.path("fileName").asText(null),
                    item.path("tagName").asText(null),
                    item.path("message").asText(null),
                    "未命名"
            );
            builder.append("- **").append(label).append("**");
            if (item.hasNonNull("sharePath")) {
                builder.append(" → ").append(item.get("sharePath").asText());
            } else if (item.hasNonNull("folderName") && StringUtils.hasText(item.get("folderName").asText())) {
                builder.append("（").append(item.get("folderName").asText()).append("）");
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "未命名";
    }
}
