package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.AiConversationContext;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AgentPlanner {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public AgentPlanner(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public AgentPlan plan(String instruction) {
        return plan(instruction, "", "");
    }

    public AgentPlan plan(String instruction, String userMemoryBlock) {
        return plan(instruction, userMemoryBlock, "");
    }

    public AgentPlan plan(String instruction, String userMemoryBlock, String conversationBlock) {
        String today = LocalDate.now().toString();
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
                今天是 %s，请正确解析「今晚」「今天」「明天」「上午」「下午」等相对时间。
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

                规则：
                1. 查列表/目录/有哪些 → 用 listNotes/listTodos/listReminders，不要 searchNotes 代替
                2. 问文档内容/总结/解释 → searchNotes + summarize；若需从正文提取任务 → extractTodos
                3. 单纯待办/提醒，无文档需求 → createTodo（± createReminder），不要 createNote
                4. 修改/删除/完成 → 先用 list/search 定位，再 update/delete/complete
                5. 写操作按依赖顺序；各步骤独立，用户可只执行部分
                6. dueAt/triggerAt 用 ISO 本地时间 YYYY-MM-DDTHH:mm:ss
                7. reply 用通俗中文说明将要做什么；查列表类问题 reply 可简短（结果由系统列出）
                8. 结合对话历史补全省略信息
                9. 仅输出 JSON：
                {"reply":"给用户的中文说明","toolCalls":[{"tool":"listNotes","args":{"folderKeyword":"灵感"}}]}

                用户最新指令：
                %s
                """.formatted(today, memorySection, dialogSection, instruction.trim());

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
