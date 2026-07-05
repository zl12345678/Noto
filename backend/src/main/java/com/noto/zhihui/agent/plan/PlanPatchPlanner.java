package com.noto.zhihui.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlanPatchPlanner {

    private static final Pattern REMINDER_RELATIVE_SHIFT = Pattern.compile(
            "提醒.{0,8}(延迟|延后|推迟|往后|提前|往前|挪).{0,8}(一周|[一二三四五六七八九十\\d]+天)"
    );
    private static final Pattern TODO_RELATIVE_SHIFT = Pattern.compile(
            "待办.{0,8}(延迟|延后|推迟|往后|提前|往前|挪).{0,8}(一周|[一二三四五六七八九十\\d]+天)"
    );
    private static final Pattern AMBIGUOUS_RELATIVE_SHIFT = Pattern.compile(
            "^(?:延迟|延后|推迟|往后|提前|往前|挪).{0,8}(?:一周|[一二三四五六七八九十\\d]+天)$"
    );

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public PlanPatchPlanner(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public PlanPatch planPatch(String instruction, PendingPlanState pendingPlan) {
        if (!StringUtils.hasText(instruction) || pendingPlan == null || !pendingPlan.hasPendingSteps()) {
            return null;
        }
        PlanPatch deterministic = tryDeterministicPatch(instruction.trim(), pendingPlan);
        if (deterministic != null) {
            return deterministic;
        }
        return planPatchWithLlm(instruction.trim(), pendingPlan);
    }

    private PlanPatch tryDeterministicPatch(String instruction, PendingPlanState pendingPlan) {
        if (AMBIGUOUS_RELATIVE_SHIFT.matcher(instruction).find()
                && hasTool(pendingPlan, "createTodo")
                && hasTool(pendingPlan, "createReminder")) {
            PlanPatch patch = new PlanPatch();
            patch.setType("clarify");
            patch.setQuestion("你想把待办时间延迟，还是只把提醒时间延迟？");
            return patch;
        }
        Matcher reminderMatcher = REMINDER_RELATIVE_SHIFT.matcher(instruction);
        Matcher todoMatcher = TODO_RELATIVE_SHIFT.matcher(instruction);
        boolean shiftReminder = reminderMatcher.find();
        boolean shiftTodo = todoMatcher.find();
        if (shiftReminder == shiftTodo) {
            return null;
        }
        Matcher matcher = shiftReminder ? reminderMatcher : todoMatcher;
        int days = parseDurationDays(matcher.group(2));
        if (days <= 0) {
            return null;
        }
        if (isBackwardShift(matcher.group(1))) {
            days = -days;
        }
        String targetTool = shiftReminder ? "createReminder" : "createTodo";
        String field = shiftReminder ? "triggerAt" : "dueAt";
        PendingPlanStep target = firstStepByTool(pendingPlan, targetTool);
        if (target == null || target.getArgs() == null || target.getArgs().get(field) == null) {
            return null;
        }
        LocalDateTime shifted;
        try {
            shifted = LocalDateTime.parse(String.valueOf(target.getArgs().get(field))).plusDays(days);
        } catch (Exception ex) {
            return null;
        }
        PlanPatch patch = new PlanPatch();
        patch.setType("modify_pending_plan");
        patch.setTargetTool(targetTool);
        patch.setTargetStepId(target.getStepId());
        patch.setUpdates(Map.of(field, shifted.toString()));
        return patch;
    }

    private PlanPatch planPatchWithLlm(String instruction, PendingPlanState pendingPlan) {
        try {
            String pendingJson = objectMapper.writeValueAsString(pendingPlan);
            String prompt = """
                    你是 Noto 知微的计划补丁生成器。用户正在修改一个尚未确认执行的 pending plan。
                    你只能输出 JSON，不要 markdown。不要直接执行工具。

                    输出格式：
                    {"type":"modify_pending_plan","targetTool":"createReminder","targetStepId":"step_2","updates":{"triggerAt":"2026-07-13T09:00:00"}}
                    或：
                    {"type":"clarify","question":"你想修改待办时间还是提醒时间？"}

                    规则：
                    1. 只修改用户明确要求修改的字段。
                    2. 用户说提醒，只改 createReminder；用户说待办，只改 createTodo。
                    3. 用户没说清目标，且 pending plan 里同时有待办和提醒，输出 clarify。
                    4. title/message/todoTitle 这类文本修改，直接在 updates 里给新值。
                    5. 无法理解时输出 clarify。

                    当前 pending plan：
                    %s

                    用户最新指令：
                    %s
                    """.formatted(pendingJson, instruction);
            String raw = chatModel.chat(prompt);
            JsonNode node = objectMapper.readTree(extractJson(raw));
            PlanPatch patch = new PlanPatch();
            patch.setType(node.path("type").asText("clarify"));
            patch.setQuestion(node.path("question").asText(null));
            patch.setTargetTool(node.path("targetTool").asText(null));
            patch.setTargetStepId(node.path("targetStepId").asText(null));
            patch.setUpdates(readUpdates(node.get("updates")));
            if (patch.isModifyPendingPlan() && patch.getUpdates().isEmpty()) {
                patch.setType("clarify");
                patch.setQuestion("我还不确定你想修改方案里的哪一项。");
            }
            return patch;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> readUpdates(JsonNode node) {
        Map<String, Object> updates = new HashMap<>();
        if (node == null || !node.isObject()) {
            return updates;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isNumber()) {
                updates.put(entry.getKey(), value.numberValue());
            } else if (value.isBoolean()) {
                updates.put(entry.getKey(), value.booleanValue());
            } else {
                updates.put(entry.getKey(), value.asText());
            }
        });
        return updates;
    }

    private boolean hasTool(PendingPlanState pendingPlan, String tool) {
        return pendingPlan.getSteps().stream().anyMatch(step -> tool.equals(step.getTool()));
    }

    private PendingPlanStep firstStepByTool(PendingPlanState pendingPlan, String tool) {
        return pendingPlan.getSteps().stream()
                .filter(step -> tool.equals(step.getTool()))
                .findFirst()
                .orElse(null);
    }

    private int parseDurationDays(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        String value = text.trim();
        if ("一周".equals(value)) {
            return 7;
        }
        if (value.endsWith("天")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 0;
        };
    }

    private boolean isBackwardShift(String verb) {
        return "提前".equals(verb) || "往前".equals(verb);
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
