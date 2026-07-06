package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.agent.plan.PendingPlanState;
import com.noto.zhihui.agent.plan.PendingPlanStep;
import com.noto.zhihui.agent.plan.PlanPatch;
import com.noto.zhihui.agent.plan.PlanPatchPlanner;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.AiConversationContext;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AgentPlanner {

    private static final Pattern REMINDER_RELATIVE_SHIFT = Pattern.compile(
            "提醒.{0,8}(延迟|延后|推迟|往后|提前|往前|挪).{0,8}(一周|[一二三四五六七八九十\\d]+天)"
    );
    private static final Pattern TODO_RELATIVE_SHIFT = Pattern.compile(
            "待办.{0,8}(延迟|延后|推迟|往后|提前|往前|挪).{0,8}(一周|[一二三四五六七八九十\\d]+天)"
    );
    private static final Pattern AMBIGUOUS_RELATIVE_SHIFT = Pattern.compile(
            "^(?:延迟|延后|推迟|往后|提前|往前|挪).{0,8}(?:一周|[一二三四五六七八九十\\d]+天)$"
    );
    private static final Pattern ADD_REMINDER_TO_PENDING_TODO = Pattern.compile(
            "(?:加|增加|添加|新建|新增|创建|设|设置).{0,24}提醒"
                    + "|提醒.{0,12}(?:上个|这个|待办|代办)"
                    + "|(?:上个|这个).{0,8}(?:待办|代办).{0,12}提醒"
    );
    private static final Pattern PENDING_PLAN_LINE = Pattern.compile("^-\\s*(.+)$");

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PlanPatchPlanner planPatchPlanner;

    public AgentPlanner(ChatModel chatModel, ObjectMapper objectMapper, PlanPatchPlanner planPatchPlanner) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.planPatchPlanner = planPatchPlanner;
    }

    public AgentPlan plan(String instruction) {
        return plan(instruction, "", "");
    }

    public AgentPlan plan(String instruction, String userMemoryBlock) {
        return plan(instruction, userMemoryBlock, "");
    }

    public AgentPlan plan(String instruction, String userMemoryBlock, String conversationBlock) {
        AgentPlan reminderPlan = tryAddReminderToPendingTodo(instruction, conversationBlock);
        if (reminderPlan != null) {
            return reminderPlan;
        }
        AgentPlan patchedPlan = tryPlanPatch(instruction, conversationBlock);
        if (patchedPlan != null) {
            return patchedPlan;
        }
        AgentPlan relativePendingEdit = tryPlanRelativePendingEdit(instruction, conversationBlock);
        if (relativePendingEdit != null) {
            return relativePendingEdit;
        }

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate todayDate = LocalDate.now(zoneId);
        String today = todayDate.toString();
        String weekday = todayDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        String nextMonday = todayDate.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                .toString();
        String memorySection = StringUtils.hasText(userMemoryBlock)
                ? userMemoryBlock.trim() + "\n\n"
                : "";
        String dialogSection = StringUtils.hasText(conversationBlock)
                ? """
                近期对话（用于补全省略信息，如「那就帮我创建」「同样安排提醒」）：
                %s

                """.formatted(conversationBlock.trim())
                : "";
        String prompt = """
                你是 Noto 知微的系统操控助手。用户希望通过自然语言完全操控知识库：文档、待办、提醒。
                你的职责是理解需求，规划正确的工具调用序列；读操作直接查，写操作需用户确认后执行。
                当前时区是 Asia/Shanghai。今天是 %s（%s）。
                请正确解析「今晚」「今天」「明天」「下周一」「下周二」「上午」「下午」等相对时间。
                重要：下周一表示今天之后的下一个星期一；以今天 %s 为例，下周一是 %s，不是周二。
                若下方有用户偏好记忆，规划时优先贴合其主攻项目与办事偏好。

                %s%s【只读 · 立即执行】
                - listNotes(keyword?, folderKeyword?): 列出文档目录（如「灵感有哪些文档」→ folderKeyword=灵感）
                - searchNotes(keyword): 按关键词搜索文档
                - listTodos(keyword?, status?): 列出待办；status 0待处理/1进行中/2已完成
                - searchTodos(keyword): 按标题搜索待办
                - listReminders(status?): 列出提醒
                - summarize(noteId): 生成文档摘要

                【写入 · 需用户确认】
                - createNote(title, content): 创建文档
                - updateNote(noteId?, title?, content?): 更新文档（缺 noteId 时用 title 定位）
                - deleteNote(noteId?, title?): 删除文档
                - extractTodos(noteId 或 title+content): 从文档提取待办
                - createTodo(title, noteId?, horizon, dueAt): 创建待办
                - updateTodo(todoId?, title?, newTitle?, dueAt?, status?): 更新待办
                - completeTodo(todoId?, title?): 标记待办完成
                - deleteTodo(todoId?, title?): 删除待办
                - createReminder(triggerAt, message, todoId?, todoTitle?): 创建提醒
                - cancelReminder(reminderId): 取消提醒

                【网盘 · 只读】
                - listDriveFiles(keyword?, folderKeyword?): 列出网盘文件
                - listDriveFolders(): 列出网盘文件夹

                【网盘 · 需确认】
                - deleteDriveFile(attachmentId?, fileName?): 删除网盘文件
                - moveDriveFile(attachmentId?, fileName?, folderId?, folderName?): 移动文件到网盘文件夹
                - linkFileToNote(attachmentId?, fileName?, noteId?, noteTitle?): 关联网盘文件到文档

                【分享 · 只读/写入】
                - listShares(): 列出我的分享
                - createNoteShare(noteId?, noteTitle?, expiresInDays?, password?): 分享文档
                - createFileShare(attachmentId?, fileName?, expiresInDays?, password?): 分享网盘文件
                - revokeShare(token 或 resourceType+noteId/attachmentId): 取消分享

                【标签与分组 · 只读/写入】
                - listTags(): 列出标签
                - createTag(name, color?): 创建标签
                - deleteTag(tagId?, tagName?): 删除标签
                - tagNote(noteId?, noteTitle?, tagNames): 给文档打标签（多个用逗号）
                - listNoteFolders(): 列出文档分组
                - createNoteFolder(name, parentId?): 创建文档分组
                - createDriveFolder(name, parentId?): 创建网盘文件夹
                - moveNoteToFolder(noteId?, noteTitle?, folderId?, folderName?): 移动文档到分组

                典型示例：
                - 「灵感有哪些文档」→ listNotes(folderKeyword=灵感)
                - 「我有哪些逾期待办」→ listTodos(keyword=, status=0) 或 listTodos
                - 「明天上午提醒我剪头发」→ createTodo + createReminder
                - 「把 XX 待办标记完成」→ completeTodo(title=XX)
                - 「网盘里有哪些 PDF」→ listDriveFiles(keyword=pdf)
                - 「分享这篇文档，密码 1234」→ createNoteShare
                - 「给文档打上产品标签」→ tagNote(tagNames=产品)
                - 「删除文档 YY」→ searchNotes 或 deleteNote(title=YY)
                - 「这篇会议纪要有哪些行动项」→ searchNotes → summarize 或 extractTodos
                - 上文有待确认方案「createTodo title=交周报 dueAt=2026-07-06T18:00:00」，用户说「改成周三上午10点」
                  → 重新输出 createTodo(title=交周报, dueAt=新的 ISO 时间)，不要创建无关新待办

                规则：
                1. 查列表/目录/有哪些 → 用 listNotes/listTodos/listReminders，不要 searchNotes 代替
                2. 问文档内容/总结/解释 → searchNotes + summarize；若需从正文提取任务 → extractTodos
                3. 单纯待办/提醒，无文档需求 → createTodo（± createReminder），不要 createNote
                4. 修改/删除/完成 → 先用 list/search 定位，再 update/delete/complete
                5. 写操作按依赖顺序；各步骤独立，用户可只执行部分
                6. dueAt/triggerAt 用 ISO 本地时间 YYYY-MM-DDTHH:mm:ss；不要输出带 Z 的 UTC 时间；不要把星期一算成星期二
                7. reply 用通俗中文说明将要做什么；查列表类问题 reply 可简短（结果由系统列出）
                8. 结合对话历史补全省略信息
                9. 如果近期对话里有「待确认方案」：
                   - 用户说「改下时间」「换成明天」「推迟到下周三」「改成上午10点」时，表示修改最近一条 pending_confirm 的 createTodo/createReminder
                   - 继承原方案中的 title/todoTitle/message，只替换用户明确修改的字段
                   - 不要输出 updateTodo，除非上下文明确已有已创建 todoId；待确认方案尚未入库时应重新生成 createTodo/createReminder
                   - 若同时有 createTodo 和 createReminder，用户泛泛改时间通常要同时更新 dueAt 和 triggerAt
                   - 用户明确说「提醒延迟一周/提醒推迟3天」时，只把 createReminder.triggerAt 按原 triggerAt 偏移；createTodo.dueAt 保持不变
                   - 用户明确说「待办延迟一周/待办推迟3天」时，只把 createTodo.dueAt 按原 dueAt 偏移；createReminder.triggerAt 保持不变，除非用户也提到提醒
                   - 待确认方案尚未入库且同时有 createTodo/createReminder 时，重新输出完整方案：未修改的 createTodo/createReminder 也要保留，避免提醒失去待办依赖
                   - 如果多个待确认项无法判断改哪个，reply 里请用户选择，不要贸然写入
                10. 仅输出 JSON：
                {"reply":"给用户的中文说明","toolCalls":[{"tool":"listNotes","args":{"folderKeyword":"灵感"}}]}

                用户最新指令：
                %s
                """.formatted(today, weekday, today, nextMonday, memorySection, dialogSection, instruction.trim());

        try {
            String raw = chatModel.chat(prompt);
            return parsePlan(raw, instruction, conversationBlock);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "规划失败：" + ex.getMessage());
        }
    }

    private AgentPlan parsePlan(String raw, String instruction, String conversationBlock) {
        AgentPlan plan = new AgentPlan();
        plan.setReply("已理解你的需求，请确认下方方案。");
        plan.setToolCalls(new ArrayList<>());
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            if (node.hasNonNull("reply")) {
                plan.setReply(node.get("reply").asText());
            }
            JsonNode calls = node.get("toolCalls");
            if (calls != null && calls.isArray()) {
                calls.forEach(item -> {
                    if (item == null || !item.isObject()) {
                        return;
                    }
                    String tool = item.has("tool") ? item.get("tool").asText() : null;
                    if (!StringUtils.hasText(tool)) {
                        return;
                    }
                    Map<String, Object> args = readArgs(item.get("args"));
                    plan.getToolCalls().add(new AgentToolCall(tool, args));
                });
            }
        } catch (Exception ignored) {
            // fallback below
        }
        if (plan.getToolCalls().isEmpty()) {
            String fallbackKeyword = StringUtils.hasText(instruction)
                    ? instruction.trim()
                    : AiConversationContext.extractLastUserTopic(conversationBlock);
            if (!StringUtils.hasText(fallbackKeyword)) {
                fallbackKeyword = instruction.trim();
            }
            plan.getToolCalls().add(new AgentToolCall("listNotes", Map.of("folderKeyword", fallbackKeyword)));
            plan.setReply("正在为你查询知识库目录…");
        }
        return plan;
    }

    private AgentPlan tryAddReminderToPendingTodo(String instruction, String conversationBlock) {
        if (!StringUtils.hasText(instruction) || !StringUtils.hasText(conversationBlock)) {
            return null;
        }
        String normalized = instruction.trim().replaceAll("\\s+", "");
        if (!ADD_REMINDER_TO_PENDING_TODO.matcher(normalized).find()) {
            return null;
        }
        List<PendingPlanItem> pendingItems = readPendingPlanItems(conversationBlock);
        if (pendingItems.isEmpty() || pendingItems.stream().anyMatch(item -> "createReminder".equals(item.tool()))) {
            return null;
        }
        List<PendingPlanItem> todos = pendingItems.stream()
                .filter(item -> "createTodo".equals(item.tool()))
                .toList();
        PendingPlanItem todo = selectPendingTodoForReminder(normalized, todos);
        if (todo == null) {
            AgentPlan clarify = new AgentPlan();
            clarify.setReply("可以加提醒。请告诉我是给哪个待办加提醒。");
            clarify.setToolCalls(new ArrayList<>());
            return clarify;
        }
        String title = stringArg(todo.args(), "title");
        String dueAt = stringArg(todo.args(), "dueAt");
        if (!StringUtils.hasText(title) || !StringUtils.hasText(dueAt)) {
            AgentPlan clarify = new AgentPlan();
            clarify.setReply("可以，我会给这个待办加提醒。请告诉我提醒时间。");
            clarify.setToolCalls(new ArrayList<>());
            return clarify;
        }

        AgentPlan plan = new AgentPlan();
        plan.setReply("已给待确认的待办补充提醒，请确认下方新方案。");
        plan.setToolCalls(new ArrayList<>());
        for (PendingPlanItem item : pendingItems) {
            plan.getToolCalls().add(new AgentToolCall(item.tool(), new HashMap<>(item.args())));
        }
        plan.getToolCalls().add(new AgentToolCall("createReminder", new HashMap<>(Map.of(
                "todoTitle", title,
                "message", title,
                "triggerAt", dueAt
        ))));
        return plan;
    }

    private PendingPlanItem selectPendingTodoForReminder(String normalizedInstruction, List<PendingPlanItem> todos) {
        if (todos.isEmpty()) {
            return null;
        }
        if (todos.size() == 1) {
            return todos.get(0);
        }
        return todos.stream()
                .filter(item -> {
                    String title = stringArg(item.args(), "title");
                    return StringUtils.hasText(title)
                            && normalizedInstruction.contains(title.replaceAll("\\s+", ""));
                })
                .findFirst()
                .orElse(null);
    }

    private AgentPlan tryPlanPatch(String instruction, String conversationBlock) {
        PendingPlanState pendingPlan = readPendingPlanState(conversationBlock);
        if (pendingPlan == null || !pendingPlan.hasPendingSteps()) {
            return null;
        }
        PlanPatch patch = planPatchPlanner.planPatch(instruction, pendingPlan);
        if (patch == null) {
            return null;
        }
        AgentPlan plan = new AgentPlan();
        plan.setToolCalls(new ArrayList<>());
        if (patch.isClarify()) {
            plan.setReply(StringUtils.hasText(patch.getQuestion())
                    ? patch.getQuestion()
                    : "我还不确定你想修改方案里的哪一项。");
            return plan;
        }
        if (!patch.isModifyPendingPlan()) {
            return null;
        }
        boolean changed = false;
        for (PendingPlanStep step : pendingPlan.getSteps()) {
            Map<String, Object> args = new HashMap<>(step.getArgs() == null ? Map.of() : step.getArgs());
            if (matchesPatchTarget(step, patch)) {
                args.putAll(patch.getUpdates());
                changed = true;
            }
            plan.getToolCalls().add(new AgentToolCall(step.getTool(), args));
        }
        if (!changed) {
            plan.setToolCalls(new ArrayList<>());
            plan.setReply("我找不到你要修改的待确认项，请说明是改待办还是提醒。");
            return plan;
        }
        plan.setReply("已根据你的补充修改待确认方案，请确认下方新方案。");
        return plan;
    }

    private boolean matchesPatchTarget(PendingPlanStep step, PlanPatch patch) {
        if (StringUtils.hasText(patch.getTargetStepId()) && patch.getTargetStepId().equals(step.getStepId())) {
            return true;
        }
        return StringUtils.hasText(patch.getTargetTool()) && patch.getTargetTool().equals(step.getTool());
    }

    private PendingPlanState readPendingPlanState(String conversationBlock) {
        if (!StringUtils.hasText(conversationBlock)) {
            return null;
        }
        for (String line : conversationBlock.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("PENDING_PLAN_JSON:")) {
                continue;
            }
            String json = trimmed.substring("PENDING_PLAN_JSON:".length()).trim();
            try {
                PendingPlanState state = objectMapper.readValue(json, PendingPlanState.class);
                if (state != null && state.hasPendingSteps()) {
                    return state;
                }
            } catch (Exception ignored) {
                // fallback below
            }
        }
        List<PendingPlanItem> items = readPendingPlanItems(conversationBlock);
        if (items.isEmpty()) {
            return null;
        }
        PendingPlanState state = new PendingPlanState();
        state.setPlanId("pending-plan");
        state.setStatus("pending_confirm");
        int index = 1;
        for (PendingPlanItem item : items) {
            PendingPlanStep step = new PendingPlanStep();
            step.setStepId("step_" + index++);
            step.setTool(item.tool());
            step.setArgs(item.args());
            step.setStatus("pending_confirm");
            state.getSteps().add(step);
        }
        return state;
    }

    private AgentPlan tryPlanRelativePendingEdit(String instruction, String conversationBlock) {
        if (!StringUtils.hasText(instruction) || !StringUtils.hasText(conversationBlock)
                || !conversationBlock.contains("待确认方案")) {
            return null;
        }
        if (AMBIGUOUS_RELATIVE_SHIFT.matcher(instruction.trim()).find()) {
            AgentPlan clarify = new AgentPlan();
            clarify.setReply("你想把待办时间延迟，还是只把提醒时间延迟？");
            clarify.setToolCalls(new ArrayList<>());
            return clarify;
        }
        Matcher reminderMatcher = REMINDER_RELATIVE_SHIFT.matcher(instruction.trim());
        Matcher todoMatcher = TODO_RELATIVE_SHIFT.matcher(instruction.trim());
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

        List<PendingPlanItem> pendingItems = readPendingPlanItems(conversationBlock);
        if (pendingItems.isEmpty()) {
            return null;
        }

        AgentPlan plan = new AgentPlan();
        plan.setToolCalls(new ArrayList<>());
        boolean changed = false;
        for (PendingPlanItem item : pendingItems) {
            Map<String, Object> args = new HashMap<>(item.args());
            if (shiftReminder && "createReminder".equals(item.tool())) {
                String triggerAt = stringArg(args, "triggerAt");
                LocalDateTime shifted = shiftIsoTime(triggerAt, days);
                if (shifted == null) {
                    return null;
                }
                args.put("triggerAt", shifted.toString());
                changed = true;
            } else if (shiftTodo && "createTodo".equals(item.tool())) {
                String dueAt = stringArg(args, "dueAt");
                LocalDateTime shifted = shiftIsoTime(dueAt, days);
                if (shifted == null) {
                    return null;
                }
                args.put("dueAt", shifted.toString());
                changed = true;
            }
            plan.getToolCalls().add(new AgentToolCall(item.tool(), args));
        }
        if (!changed) {
            return null;
        }
        plan.setReply(shiftReminder
                ? "已按你的要求把提醒时间调整了，待办时间保持不变，请确认下方新方案。"
                : "已按你的要求把待办时间调整了，提醒时间保持不变，请确认下方新方案。");
        return plan;
    }

    private List<PendingPlanItem> readPendingPlanItems(String conversationBlock) {
        List<PendingPlanItem> structured = readPendingPlanItemsFromJson(conversationBlock);
        if (!structured.isEmpty()) {
            return structured;
        }
        List<PendingPlanItem> items = new ArrayList<>();
        for (String line : conversationBlock.split("\\R")) {
            Matcher matcher = PENDING_PLAN_LINE.matcher(line.trim());
            if (!matcher.find()) {
                continue;
            }
            Map<String, Object> args = new HashMap<>();
            String tool = null;
            for (String part : matcher.group(1).split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String key = pair[0].trim();
                String value = pair[1].trim();
                if ("tool".equals(key)) {
                    tool = value;
                } else if (!"stepId".equals(key) && !"status".equals(key)) {
                    args.put(key, value);
                }
            }
            if (StringUtils.hasText(tool) && ("createTodo".equals(tool) || "createReminder".equals(tool))) {
                items.add(new PendingPlanItem(tool, args));
            }
        }
        return items;
    }

    private List<PendingPlanItem> readPendingPlanItemsFromJson(String conversationBlock) {
        List<PendingPlanItem> items = new ArrayList<>();
        for (String line : conversationBlock.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("PENDING_PLAN_JSON:")) {
                continue;
            }
            String json = trimmed.substring("PENDING_PLAN_JSON:".length()).trim();
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode steps = root.get("steps");
                if (steps == null || !steps.isArray()) {
                    continue;
                }
                for (JsonNode step : steps) {
                    if (step == null || !step.isObject()) {
                        continue;
                    }
                    String tool = step.path("tool").asText("");
                    if (!StringUtils.hasText(tool) || (!"createTodo".equals(tool) && !"createReminder".equals(tool))) {
                        continue;
                    }
                    Map<String, Object> args = readArgs(step.get("args"));
                    items.add(new PendingPlanItem(tool, args));
                }
            } catch (Exception ignored) {
                // fallback to readable pending-plan lines
            }
        }
        return items;
    }

    private LocalDateTime shiftIsoTime(String value, int days) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim()).plusDays(days);
        } catch (Exception ex) {
            return null;
        }
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

    private String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private record PendingPlanItem(String tool, Map<String, Object> args) {
    }

    public static Comparator<String> stepIdOrder() {
        return Comparator.comparingInt(AgentPlanner::stepIndex);
    }

    private static int stepIndex(String stepId) {
        if (!StringUtils.hasText(stepId)) {
            return Integer.MAX_VALUE;
        }
        String digits = stepId.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private Map<String, Object> readArgs(JsonNode node) {
        Map<String, Object> args = new HashMap<>();
        if (node == null || !node.isObject()) {
            return args;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                args.put(entry.getKey(), value.numberValue());
            } else {
                args.put(entry.getKey(), value.asText());
            }
        }
        return args;
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    public record AgentToolCall(String tool, Map<String, Object> args) {
    }

    public static final class AgentPlan {
        private String reply;
        private List<AgentToolCall> toolCalls;

        public String getReply() {
            return reply;
        }

        public void setReply(String reply) {
            this.reply = reply;
        }

        public List<AgentToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<AgentToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }
}
