package com.noto.zhihui.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.util.ExtractedTodoDueHelper;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.NoteRetrievalService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.ReminderService;
import com.noto.zhihui.service.SearchService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.vo.ai.NoteSummaryVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.reminder.ReminderVO;
import com.noto.zhihui.vo.search.SearchResultVO;
import com.noto.zhihui.vo.todo.TodoVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentToolExecutor {

    private static final int STATUS_COMPLETED = 2;

    private final SearchService searchService;
    private final AiService aiService;
    private final NoteService noteService;
    private final NoteRetrievalService noteRetrievalService;
    private final TodoService todoService;
    private final ReminderService reminderService;
    private final ObjectMapper objectMapper;
    private final AgentWorkspaceTools agentWorkspaceTools;

    public AgentToolExecutor(
            SearchService searchService,
            AiService aiService,
            NoteService noteService,
            NoteRetrievalService noteRetrievalService,
            TodoService todoService,
            ReminderService reminderService,
            ObjectMapper objectMapper,
            AgentWorkspaceTools agentWorkspaceTools
    ) {
        this.searchService = searchService;
        this.aiService = aiService;
        this.noteService = noteService;
        this.noteRetrievalService = noteRetrievalService;
        this.todoService = todoService;
        this.reminderService = reminderService;
        this.objectMapper = objectMapper;
        this.agentWorkspaceTools = agentWorkspaceTools;
    }

    public String searchNotes(String keyword) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("keyword", keyword);
        try {
            Page<SearchResultVO> page = searchService.search(
                    ctx.getUserId(), 1, 8, keyword, ctx.getWorkspaceId(), null, null
            );
            List<Map<String, Object>> items = page.getRecords().stream()
                    .map(item -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("noteId", item.getNoteId());
                        row.put("title", item.getTitle());
                        row.put("snippet", item.getSnippet());
                        return row;
                    })
                    .toList();
            String output = writeJson(Map.of("count", items.size(), "items", items));
            ctx.recordStep("searchNotes", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "搜索失败：" + ex.getMessage();
            ctx.recordStep("searchNotes", input, output, false, null);
            return output;
        }
    }

    public String listNotes(String keyword, String folderKeyword) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("keyword", keyword);
        input.put("folderKeyword", folderKeyword);
        try {
            String question = buildCatalogQuestion(keyword, folderKeyword);
            String catalog = noteRetrievalService.buildCatalogContext(
                    ctx.getUserId(), ctx.getWorkspaceId(), question
            );
            String output = writeJson(Map.of("catalog", catalog, "question", question));
            ctx.recordStep("listNotes", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出文档失败：" + ex.getMessage();
            ctx.recordStep("listNotes", input, output, false, null);
            return output;
        }
    }

    public String listTodos(String keyword, Integer status) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("keyword", keyword);
        input.put("status", status);
        try {
            Page<TodoVO> page = todoService.pageTodos(
                    ctx.getUserId(), 1, 20, ctx.getWorkspaceId(), status, null, null,
                    StringUtils.hasText(keyword) ? keyword.trim() : null,
                    null
            );
            List<Map<String, Object>> items = page.getRecords().stream()
                    .map(this::todoRow)
                    .toList();
            String output = writeJson(Map.of("count", page.getTotal(), "items", items));
            ctx.recordStep("listTodos", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出待办失败：" + ex.getMessage();
            ctx.recordStep("listTodos", input, output, false, null);
            return output;
        }
    }

    public String searchTodos(String keyword) {
        return listTodos(keyword, null);
    }

    public String listReminders(Integer status) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("status", status != null ? status : "");
        try {
            Page<ReminderVO> page = reminderService.pageReminders(
                    ctx.getUserId(), 1, 20, ctx.getWorkspaceId(), status, null
            );
            List<Map<String, Object>> items = page.getRecords().stream()
                    .map(item -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("reminderId", item.getId());
                        row.put("message", item.getMessage());
                        row.put("triggerAt", item.getTriggerAt() != null ? item.getTriggerAt().toString() : null);
                        row.put("status", item.getStatus());
                        return row;
                    })
                    .toList();
            String output = writeJson(Map.of("count", page.getTotal(), "items", items));
            ctx.recordStep("listReminders", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "列出提醒失败：" + ex.getMessage();
            ctx.recordStep("listReminders", input, output, false, null);
            return output;
        }
    }

    public String createNote(String title, String content) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("title", title);
        input.put("content", content);

        if (!StringUtils.hasText(title)) {
            String output = "文档标题不能为空";
            ctx.recordStep("createNote", input, output, false, null);
            return output;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("title", title.trim());
        payload.put("content", content != null ? content : "");
        payload.put("contentType", "markdown");

        if (ctx.isDryRun()) {
            String output = "预览：将创建文档「" + title.trim() + "」";
            ctx.recordStep("createNote", input, output, true, payload);
            return output;
        }

        NoteCreateRequest request = new NoteCreateRequest();
        request.setWorkspaceId(ctx.getWorkspaceId());
        request.setTitle(title.trim());
        request.setContent(content);
        request.setContentType("markdown");
        NoteVO created = noteService.createNote(request, ctx.getUserId());
        ctx.setLastNoteId(created.getId());
        String output = writeJson(created);
        ctx.recordStep("createNote", input, output, false, payload);
        return output;
    }

    public String updateNote(Long noteId, String title, String content) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        noteId = resolveNoteId(noteId, title);
        Map<String, Object> input = new HashMap<>();
        input.put("noteId", noteId);
        input.put("title", title);
        input.put("content", content);

        if (noteId == null) {
            String output = "未找到要更新的文档，请先 searchNotes 或 listNotes";
            ctx.recordStep("updateNote", input, output, false, null);
            return output;
        }

        NoteVO existing = noteService.getNoteDetail(noteId, ctx.getUserId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", noteId);
        payload.put("title", StringUtils.hasText(title) ? title.trim() : existing.getTitle());
        if (content != null) {
            payload.put("content", content);
        } else if (existing.getContent() != null) {
            payload.put("content", existing.getContent());
        }
        payload.put("contentType", "markdown");

        if (ctx.isDryRun()) {
            String output = "预览：将更新文档「" + payload.get("title") + "」";
            ctx.recordStep("updateNote", input, output, true, payload);
            return output;
        }

        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setTitle(String.valueOf(payload.get("title")));
        request.setContent(payload.get("content") != null ? String.valueOf(payload.get("content")) : "");
        request.setContentType("markdown");
        NoteVO updated = noteService.updateNote(noteId, request, ctx.getUserId());
        String output = writeJson(updated);
        ctx.recordStep("updateNote", input, output, false, payload);
        return output;
    }

    public String deleteNote(Long noteId, String title) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        noteId = resolveNoteId(noteId, title);
        Map<String, Object> input = new HashMap<>();
        input.put("noteId", noteId);
        input.put("title", title);

        if (noteId == null) {
            String output = "未找到要删除的文档";
            ctx.recordStep("deleteNote", input, output, false, null);
            return output;
        }

        NoteVO existing = noteService.getNoteDetail(noteId, ctx.getUserId());
        Map<String, Object> payload = Map.of("noteId", noteId, "title", existing.getTitle());

        if (ctx.isDryRun()) {
            String output = "预览：将删除文档「" + existing.getTitle() + "」";
            ctx.recordStep("deleteNote", input, output, true, payload);
            return output;
        }

        noteService.deleteNote(noteId, ctx.getUserId());
        String output = "已删除文档「" + existing.getTitle() + "」";
        ctx.recordStep("deleteNote", input, output, false, payload);
        return output;
    }

    public String summarize(Long noteId) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("noteId", noteId);
        try {
            NoteSummaryVO summary = aiService.summarizeNote(noteId, ctx.getUserId(), null);
            String output = writeJson(summary);
            ctx.recordStep("summarize", input, output, false, null);
            return output;
        } catch (Exception ex) {
            String output = "摘要失败：" + ex.getMessage();
            ctx.recordStep("summarize", input, output, false, null);
            return output;
        }
    }

    public String extractTodos(Long noteId, String title, String content) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("noteId", noteId);
        input.put("title", title);
        input.put("content", content);

        try {
            NoteExtractTodosPreviewVO preview;
            if (noteId != null) {
                preview = aiService.previewExtractTodosFromNote(noteId, ctx.getUserId());
            } else if (StringUtils.hasText(content)) {
                preview = aiService.previewExtractTodosFromContent(
                        title, content, ctx.getWorkspaceId(), ctx.getUserId()
                );
            } else {
                String output = "提取待办需要 noteId 或 content";
                ctx.recordStep("extractTodos", input, output, false, null);
                return output;
            }

            String output = writeJson(preview);
            Map<String, Object> payload = new HashMap<>();
            payload.put("noteId", preview.getNoteId() != null ? preview.getNoteId() : noteId);
            payload.put("title", preview.getNoteTitle());
            payload.put("content", content);
            payload.put("source", "ai");
            payload.put("suggestions", preview.getSuggestions());
            payload.put("dependsOnCreateNote", noteId == null && preview.getNoteId() == null);
            ctx.recordStep("extractTodos", input, output, true, payload);
            return output;
        } catch (Exception ex) {
            String output = "提取预览失败：" + ex.getMessage();
            ctx.recordStep("extractTodos", input, output, false, null);
            return output;
        }
    }

    public String createTodo(String title, Long noteId, String horizon, String dueAt) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        if (noteId == null) {
            noteId = ctx.getLastNoteId();
        }
        Map<String, Object> input = new HashMap<>();
        input.put("title", title);
        input.put("noteId", noteId);
        input.put("horizon", horizon);
        input.put("dueAt", dueAt);

        Map<String, Object> payload = buildTodoPayload(ctx, title, noteId, horizon, dueAt);

        if (ctx.isDryRun()) {
            String output = "预览：将创建待办「" + title + "」";
            ctx.recordStep("createTodo", input, output, true, payload);
            return output;
        }

        TodoVO created = todoService.createTodo(buildTodoRequest(payload, ctx.getWorkspaceId()), ctx.getUserId());
        ctx.addTodoId(created.getId());
        String output = writeJson(created);
        ctx.recordStep("createTodo", input, output, false, payload);
        return output;
    }

    public String updateTodo(Long todoId, String title, String newTitle, String dueAt, Integer status) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        todoId = resolveTodoId(todoId, title);
        Map<String, Object> input = new HashMap<>();
        input.put("todoId", todoId);
        input.put("title", title);
        input.put("newTitle", newTitle);
        input.put("dueAt", dueAt);
        input.put("status", status);

        if (todoId == null) {
            String output = "未找到要更新的待办，请先 listTodos 或 searchTodos";
            ctx.recordStep("updateTodo", input, output, false, null);
            return output;
        }

        TodoVO existing = todoService.getTodoDetail(todoId, ctx.getUserId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("todoId", todoId);
        payload.put("title", StringUtils.hasText(newTitle) ? newTitle.trim() : existing.getTitle());
        payload.put("horizon", existing.getHorizon());
        payload.put("priority", existing.getPriority());
        if (StringUtils.hasText(dueAt)) {
            payload.put("dueAt", dueAt);
        } else if (existing.getDueAt() != null) {
            payload.put("dueAt", existing.getDueAt().toString());
        }
        if (status != null) {
            payload.put("status", status);
        }

        if (ctx.isDryRun()) {
            String output = "预览：将更新待办「" + payload.get("title") + "」";
            ctx.recordStep("updateTodo", input, output, true, payload);
            return output;
        }

        TodoUpdateRequest request = new TodoUpdateRequest();
        request.setTitle(String.valueOf(payload.get("title")));
        request.setHorizon(String.valueOf(payload.get("horizon")));
        request.setPriority((Integer) payload.get("priority"));
        if (payload.get("dueAt") != null) {
            request.setDueAt(ExtractedTodoDueHelper.parseDueAt(String.valueOf(payload.get("dueAt"))));
        }
        TodoVO updated = todoService.updateTodo(todoId, request, ctx.getUserId());
        if (status != null) {
            updated = todoService.updateStatus(todoId, status, ctx.getUserId());
        }
        String output = writeJson(updated);
        ctx.recordStep("updateTodo", input, output, false, payload);
        return output;
    }

    public String completeTodo(Long todoId, String title) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        todoId = resolveTodoId(todoId, title);
        Map<String, Object> input = new HashMap<>();
        input.put("todoId", todoId);
        input.put("title", title);

        if (todoId == null) {
            String output = "未找到要完成的待办";
            ctx.recordStep("completeTodo", input, output, false, null);
            return output;
        }

        TodoVO existing = todoService.getTodoDetail(todoId, ctx.getUserId());
        Map<String, Object> payload = Map.of("todoId", todoId, "status", STATUS_COMPLETED);

        if (ctx.isDryRun()) {
            String output = "预览：将标记待办「" + existing.getTitle() + "」为已完成";
            ctx.recordStep("completeTodo", input, output, true, payload);
            return output;
        }

        TodoVO updated = todoService.updateStatus(todoId, STATUS_COMPLETED, ctx.getUserId());
        String output = writeJson(updated);
        ctx.recordStep("completeTodo", input, output, false, payload);
        return output;
    }

    public String deleteTodo(Long todoId, String title) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        todoId = resolveTodoId(todoId, title);
        Map<String, Object> input = new HashMap<>();
        input.put("todoId", todoId);
        input.put("title", title);

        if (todoId == null) {
            String output = "未找到要删除的待办";
            ctx.recordStep("deleteTodo", input, output, false, null);
            return output;
        }

        TodoVO existing = todoService.getTodoDetail(todoId, ctx.getUserId());
        Map<String, Object> payload = Map.of("todoId", todoId, "title", existing.getTitle());

        if (ctx.isDryRun()) {
            String output = "预览：将删除待办「" + existing.getTitle() + "」";
            ctx.recordStep("deleteTodo", input, output, true, payload);
            return output;
        }

        todoService.deleteTodo(todoId, ctx.getUserId());
        String output = "已删除待办「" + existing.getTitle() + "」";
        ctx.recordStep("deleteTodo", input, output, false, payload);
        return output;
    }

    public String createReminder(Long todoId, String triggerAt, String message, String todoTitle) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = new HashMap<>();
        input.put("todoId", todoId);
        input.put("triggerAt", triggerAt);
        input.put("message", message);
        input.put("todoTitle", todoTitle);

        var trigger = ExtractedTodoDueHelper.parseDueAt(triggerAt);
        if (trigger == null) {
            String output = "提醒时间无效，请使用 ISO 本地时间，如 2026-06-09T20:00:00";
            ctx.recordStep("createReminder", input, output, false, null);
            return output;
        }

        Long resolvedTodoId = todoId;
        if (resolvedTodoId == null || resolvedTodoId <= 0) {
            resolvedTodoId = resolveTodoId(null, todoTitle);
        }
        if (resolvedTodoId == null || resolvedTodoId <= 0) {
            resolvedTodoId = ctx.getLastTodoId();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("todoId", resolvedTodoId);
        payload.put("todoTitle", todoTitle);
        payload.put("triggerAt", trigger.toString());
        payload.put("message", message);
        payload.put("reminderType", "due");
        payload.put("dependsOnExtractTodos", resolvedTodoId == null || resolvedTodoId <= 0);

        if (ctx.isDryRun()) {
            String output = resolvedTodoId != null && resolvedTodoId > 0
                    ? "预览：将为待办 #" + resolvedTodoId + " 创建提醒（" + trigger + "）"
                    : "预览：将在待办创建后，于 " + trigger + " 发送提醒「" + (message != null ? message : "") + "」";
            ctx.recordStep("createReminder", input, output, true, payload);
            return output;
        }

        if (resolvedTodoId == null || resolvedTodoId <= 0) {
            String output = "无法创建提醒：缺少 todoId，请先确认提取/创建待办步骤";
            ctx.recordStep("createReminder", input, output, false, null);
            return output;
        }

        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setWorkspaceId(ctx.getWorkspaceId());
        request.setTodoId(resolvedTodoId);
        request.setTriggerAt(trigger);
        request.setMessage(message);
        request.setReminderType("due");
        ReminderVO created = reminderService.createReminder(request, ctx.getUserId());
        String output = writeJson(created);
        ctx.recordStep("createReminder", input, output, false, payload);
        return output;
    }

    public String cancelReminder(Long reminderId) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Map<String, Object> input = Map.of("reminderId", reminderId);

        if (reminderId == null) {
            String output = "缺少 reminderId";
            ctx.recordStep("cancelReminder", input, output, false, null);
            return output;
        }

        Map<String, Object> payload = Map.of("reminderId", reminderId);

        if (ctx.isDryRun()) {
            String output = "预览：将取消提醒 #" + reminderId;
            ctx.recordStep("cancelReminder", input, output, true, payload);
            return output;
        }

        ReminderVO cancelled = reminderService.cancelReminder(reminderId, ctx.getUserId());
        String output = writeJson(cancelled);
        ctx.recordStep("cancelReminder", input, output, false, payload);
        return output;
    }

    public String execute(String tool, Map<String, Object> args) {
        return switch (tool) {
            case "searchNotes" -> searchNotes(String.valueOf(args.getOrDefault("keyword", "")));
            case "listNotes" -> listNotes(
                    args.get("keyword") != null ? String.valueOf(args.get("keyword")) : null,
                    args.get("folderKeyword") != null ? String.valueOf(args.get("folderKeyword")) : null
            );
            case "listTodos" -> listTodos(
                    args.get("keyword") != null ? String.valueOf(args.get("keyword")) : null,
                    asInteger(args.get("status"))
            );
            case "searchTodos" -> searchTodos(String.valueOf(args.getOrDefault("keyword", "")));
            case "listReminders" -> listReminders(asInteger(args.get("status")));
            case "createNote" -> createNote(
                    args.get("title") != null ? String.valueOf(args.get("title")) : null,
                    args.get("content") != null ? String.valueOf(args.get("content")) : null
            );
            case "updateNote" -> updateNote(
                    asLong(args.get("noteId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null,
                    args.get("content") != null ? String.valueOf(args.get("content")) : null
            );
            case "deleteNote" -> deleteNote(
                    asLong(args.get("noteId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null
            );
            case "summarize" -> summarize(asLong(args.get("noteId")));
            case "extractTodos" -> extractTodos(
                    asLong(args.get("noteId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null,
                    args.get("content") != null ? String.valueOf(args.get("content")) : null
            );
            case "createTodo" -> createTodo(
                    String.valueOf(args.get("title")),
                    asLong(args.get("noteId")),
                    String.valueOf(args.getOrDefault("horizon", "action")),
                    args.get("dueAt") != null ? String.valueOf(args.get("dueAt")) : null
            );
            case "updateTodo" -> updateTodo(
                    asLong(args.get("todoId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null,
                    args.get("newTitle") != null ? String.valueOf(args.get("newTitle")) : null,
                    args.get("dueAt") != null ? String.valueOf(args.get("dueAt")) : null,
                    asInteger(args.get("status"))
            );
            case "completeTodo" -> completeTodo(
                    asLong(args.get("todoId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null
            );
            case "deleteTodo" -> deleteTodo(
                    asLong(args.get("todoId")),
                    args.get("title") != null ? String.valueOf(args.get("title")) : null
            );
            case "createReminder" -> createReminder(
                    asLong(args.get("todoId")),
                    String.valueOf(args.get("triggerAt")),
                    args.get("message") != null ? String.valueOf(args.get("message")) : null,
                    args.get("todoTitle") != null ? String.valueOf(args.get("todoTitle")) : null
            );
            case "cancelReminder" -> cancelReminder(asLong(args.get("reminderId")));
            default -> agentWorkspaceTools.tryExecute(tool, args).orElse("未知工具：" + tool);
        };
    }

    private Map<String, Object> buildTodoPayload(
            AgentExecutionContext ctx,
            String title,
            Long noteId,
            String horizon,
            String dueAt
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workspaceId", ctx.getWorkspaceId());
        payload.put("noteId", noteId);
        payload.put("title", title);
        payload.put("horizon", horizon);
        payload.put("priority", 2);
        if (StringUtils.hasText(dueAt)) {
            payload.put("dueAt", ExtractedTodoDueHelper.parseDueAt(dueAt) != null ? dueAt : null);
        }
        return payload;
    }

    private TodoCreateRequest buildTodoRequest(Map<String, Object> payload, Long workspaceId) {
        TodoCreateRequest request = new TodoCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setNoteId(asLong(payload.get("noteId")));
        request.setTitle(String.valueOf(payload.get("title")));
        request.setHorizon(String.valueOf(payload.get("horizon")));
        request.setPriority((Integer) payload.get("priority"));
        if (payload.get("dueAt") != null) {
            request.setDueAt(ExtractedTodoDueHelper.parseDueAt(String.valueOf(payload.get("dueAt"))));
        }
        return request;
    }

    private Map<String, Object> todoRow(TodoVO item) {
        Map<String, Object> row = new HashMap<>();
        row.put("todoId", item.getId());
        row.put("title", item.getTitle());
        row.put("status", item.getStatus());
        row.put("statusLabel", todoStatusLabel(item.getStatus()));
        row.put("dueAt", item.getDueAt() != null ? item.getDueAt().toString() : null);
        return row;
    }

    private String todoStatusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "进行中";
            case 2 -> "已完成";
            case 3 -> "已取消";
            default -> "未知";
        };
    }

    private String buildCatalogQuestion(String keyword, String folderKeyword) {
        if (StringUtils.hasText(folderKeyword)) {
            return folderKeyword.trim() + "有哪些文档";
        }
        if (StringUtils.hasText(keyword)) {
            return keyword.trim() + "有哪些文档";
        }
        return "知识库有哪些文档";
    }

    private Long resolveNoteId(Long noteId, String title) {
        if (noteId != null && noteId > 0) {
            return noteId;
        }
        if (!StringUtils.hasText(title)) {
            return null;
        }
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Page<SearchResultVO> page = searchService.search(
                ctx.getUserId(), 1, 5, title.trim(), ctx.getWorkspaceId(), null, null
        );
        if (page.getRecords().isEmpty()) {
            return null;
        }
        return page.getRecords().get(0).getNoteId();
    }

    private Long resolveTodoId(Long todoId, String title) {
        if (todoId != null && todoId > 0) {
            return todoId;
        }
        if (!StringUtils.hasText(title)) {
            return null;
        }
        AgentExecutionContext ctx = AgentExecutionContext.current();
        Page<TodoVO> page = todoService.pageTodos(
                ctx.getUserId(), 1, 5, ctx.getWorkspaceId(), null, null, null, title.trim(), null
        );
        if (page.getRecords().isEmpty()) {
            return null;
        }
        return page.getRecords().get(0).getId();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Long.parseLong(text);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"error\":\"json serialize failed\"}";
        }
    }
}
