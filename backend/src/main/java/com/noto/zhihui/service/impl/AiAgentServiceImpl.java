package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.agent.AgentConfirmChain;
import com.noto.zhihui.agent.AgentExecutionContext;
import com.noto.zhihui.agent.AgentPlanner;
import com.noto.zhihui.agent.AgentReplyComposer;
import com.noto.zhihui.agent.AgentToolExecutor;
import com.noto.zhihui.common.constants.AiTaskStatus;
import com.noto.zhihui.common.constants.AiTaskType;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.AiAgentConfirmRequest;
import com.noto.zhihui.dto.ai.AiAgentPlanRequest;
import com.noto.zhihui.dto.ai.ConfirmExtractTodoItem;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.dto.drive.DriveFolderCreateRequest;
import com.noto.zhihui.dto.share.ShareCreateRequest;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.entity.AiTaskEntity;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.mapper.AiTaskMapper;
import com.noto.zhihui.service.AiAgentService;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.common.util.AiConversationContext;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.DriveFolderService;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.ShareLinkService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.service.ReminderService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.ai.AiAgentStepVO;
import com.noto.zhihui.vo.ai.AiAgentTaskVO;
import com.noto.zhihui.vo.note.ExtractedTodoSuggestionVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.reminder.ReminderVO;
import com.noto.zhihui.vo.share.ShareLinkVO;
import com.noto.zhihui.vo.todo.TodoVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AiAgentServiceImpl implements AiAgentService {

    private final AiTaskMapper aiTaskMapper;
    private final WorkspaceService workspaceService;
    private final AgentPlanner agentPlanner;
    private final AgentToolExecutor agentToolExecutor;
    private final NoteService noteService;
    private final AiService aiService;
    private final TodoService todoService;
    private final ReminderService reminderService;
    private final AuditLogService auditLogService;
    private final UserSettingService userSettingService;
    private final AiChatSessionService aiChatSessionService;
    private final AttachmentService attachmentService;
    private final ShareLinkService shareLinkService;
    private final TagService tagService;
    private final NoteTagService noteTagService;
    private final NoteFolderService noteFolderService;
    private final DriveFolderService driveFolderService;
    private final ObjectMapper objectMapper;

    public AiAgentServiceImpl(
            AiTaskMapper aiTaskMapper,
            WorkspaceService workspaceService,
            AgentPlanner agentPlanner,
            AgentToolExecutor agentToolExecutor,
            NoteService noteService,
            AiService aiService,
            TodoService todoService,
            ReminderService reminderService,
            AuditLogService auditLogService,
            UserSettingService userSettingService,
            AiChatSessionService aiChatSessionService,
            AttachmentService attachmentService,
            ShareLinkService shareLinkService,
            TagService tagService,
            NoteTagService noteTagService,
            NoteFolderService noteFolderService,
            DriveFolderService driveFolderService,
            ObjectMapper objectMapper
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.workspaceService = workspaceService;
        this.agentPlanner = agentPlanner;
        this.agentToolExecutor = agentToolExecutor;
        this.noteService = noteService;
        this.aiService = aiService;
        this.todoService = todoService;
        this.reminderService = reminderService;
        this.auditLogService = auditLogService;
        this.userSettingService = userSettingService;
        this.aiChatSessionService = aiChatSessionService;
        this.attachmentService = attachmentService;
        this.shareLinkService = shareLinkService;
        this.tagService = tagService;
        this.noteTagService = noteTagService;
        this.noteFolderService = noteFolderService;
        this.driveFolderService = driveFolderService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiAgentTaskVO plan(AiAgentPlanRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        AiTaskEntity task = new AiTaskEntity();
        task.setWorkspaceId(request.getWorkspaceId());
        task.setUserId(userId);
        task.setTaskType(AiTaskType.AGENT_WORKFLOW);
        task.setStatus(AiTaskStatus.PROCESSING);
        task.setInputContent(request.getInstruction().trim());
        task.setRetryCount(0);
        aiTaskMapper.insert(task);

        long started = System.currentTimeMillis();
        try {
            AgentExecutionContext context = AgentExecutionContext.begin(
                    userId, request.getWorkspaceId(), true
            );
            try {
                AgentPlanner.AgentPlan plan = agentPlanner.plan(
                        request.getInstruction().trim(),
                        userSettingService.resolveLightMemoryPromptBlock(userId),
                        resolveConversationBlock(request, userId)
                );
                for (AgentPlanner.AgentToolCall call : plan.getToolCalls()) {
                    agentToolExecutor.execute(call.tool(), call.args());
                }
                AgentTaskPayload payload = buildPayload(
                        request.getInstruction().trim(),
                        plan.getReply(),
                        context.getSteps()
                );
                task.setOutputContent(writePayload(payload));
                if (hasPendingConfirm(payload.getSteps())) {
                    if (userSettingService.isAgentTrustModeEnabled(userId)
                            && isSimpleTrustedTask(payload.getSteps())) {
                        payload.setAutoExecuted(true);
                        task.setOutputContent(writePayload(payload));
                        Set<String> approved = payload.getSteps().stream()
                                .filter(step -> "pending_confirm".equals(step.getStatus()))
                                .map(AiAgentStepVO::getId)
                                .collect(java.util.stream.Collectors.toSet());
                        AiAgentTaskVO result = applyConfirmSteps(task, payload, approved, userId);
                        auditLogService.logAiCall(
                                userId,
                                request.getWorkspaceId(),
                                "ai.agent.auto_execute",
                                "ai_task",
                                task.getId(),
                                Map.of("instruction", preview(request.getInstruction()), "stepIds", approved)
                        );
                        return result;
                    }
                    task.setStatus(AiTaskStatus.AWAITING_CONFIRM);
                } else {
                    task.setStatus(AiTaskStatus.SUCCESS);
                }
                aiTaskMapper.updateById(task);
                auditLogService.logAiCall(
                        userId,
                        request.getWorkspaceId(),
                        "ai.agent.plan",
                        "ai_task",
                        task.getId(),
                        Map.of(
                                "instruction", preview(request.getInstruction()),
                                "stepCount", payload.getSteps().size(),
                                "durationMs", System.currentTimeMillis() - started
                        )
                );
                return toVO(task, payload);
            } finally {
                AgentExecutionContext.clear();
            }
        } catch (Exception ex) {
            task.setStatus(AiTaskStatus.FAILED);
            task.setErrorMessage(trimError(ex.getMessage()));
            aiTaskMapper.updateById(task);
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "智能体规划失败：" + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public AiAgentTaskVO confirm(Long taskId, AiAgentConfirmRequest request, Long userId) {
        AiTaskEntity task = requireOwnedTask(taskId, userId);
        AgentTaskPayload payload = readPayload(task.getOutputContent());
        if (payload.getSteps() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "任务无可确认步骤");
        }

        Set<String> approved = new HashSet<>(request.getStepIds());
        AiAgentTaskVO result = applyConfirmSteps(task, payload, approved, userId);

        auditLogService.logAiCall(
                userId,
                task.getWorkspaceId(),
                "ai.agent.confirm",
                "ai_task",
                task.getId(),
                Map.of("stepIds", request.getStepIds())
        );
        return result;
    }

    @Override
    public AiAgentTaskVO getTask(Long taskId, Long userId) {
        AiTaskEntity task = requireOwnedTask(taskId, userId);
        return toVO(task, readPayload(task.getOutputContent()));
    }

    @Override
    public Page<AiAgentTaskVO> listTasks(Long userId, Long workspaceId, long page, long size) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        LambdaQueryWrapper<AiTaskEntity> wrapper = new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getUserId, userId)
                .eq(AiTaskEntity::getTaskType, AiTaskType.AGENT_WORKFLOW)
                .eq(workspaceId != null, AiTaskEntity::getWorkspaceId, workspaceId)
                .orderByDesc(AiTaskEntity::getCreatedAt);
        Page<AiTaskEntity> entityPage = aiTaskMapper.selectPage(new Page<>(page, size), wrapper);
        Page<AiAgentTaskVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(entity -> toVO(entity, readPayload(entity.getOutputContent())))
                .toList());
        return voPage;
    }

    private AiAgentTaskVO applyConfirmSteps(
            AiTaskEntity task,
            AgentTaskPayload payload,
            Set<String> approved,
            Long userId
    ) {
        markSkippedSteps(payload.getSteps(), approved);
        AgentConfirmChain chain = buildChainFromCompletedSteps(payload.getSteps());
        List<AiAgentStepVO> orderedSteps = payload.getSteps().stream()
                .sorted(Comparator.comparing(AiAgentStepVO::getId, AgentPlanner.stepIdOrder()))
                .toList();
        for (AiAgentStepVO step : orderedSteps) {
            if (!approved.contains(step.getId()) || !"pending_confirm".equals(step.getStatus())) {
                continue;
            }
            Map<String, Object> result = executeConfirmedStep(
                    step, userId, task.getWorkspaceId(), chain, payload.getSteps()
            );
            step.setResult(result);
            step.setStatus("done");
        }

        task.setOutputContent(writePayload(payload));
        task.setStatus(hasPendingConfirm(payload.getSteps()) ? AiTaskStatus.AWAITING_CONFIRM : AiTaskStatus.SUCCESS);
        aiTaskMapper.updateById(task);
        return toVO(task, payload);
    }

    private boolean isSimpleTrustedTask(List<AiAgentStepVO> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (AiAgentStepVO step : steps) {
            String tool = step.getTool();
            if ("createNote".equals(tool) || "extractTodos".equals(tool)
                    || tool.contains("delete") || tool.contains("Share") || tool.contains("revoke")) {
                return false;
            }
        }
        List<AiAgentStepVO> pending = steps.stream()
                .filter(step -> "pending_confirm".equals(step.getStatus()))
                .toList();
        if (pending.size() != 1) {
            return false;
        }
        String tool = pending.get(0).getTool();
        return "createTodo".equals(tool) || "createReminder".equals(tool) || "completeTodo".equals(tool);
    }

    private void markSkippedSteps(List<AiAgentStepVO> steps, Set<String> approved) {
        if (steps == null) {
            return;
        }
        for (AiAgentStepVO step : steps) {
            if ("pending_confirm".equals(step.getStatus()) && !approved.contains(step.getId())) {
                step.setStatus("skipped");
            }
        }
    }

    private AgentConfirmChain buildChainFromCompletedSteps(List<AiAgentStepVO> steps) {
        AgentConfirmChain chain = new AgentConfirmChain();
        if (steps == null) {
            return chain;
        }
        steps.stream()
                .sorted(Comparator.comparing(AiAgentStepVO::getId, AgentPlanner.stepIdOrder()))
                .forEach(step -> {
                    if (!"done".equals(step.getStatus())) {
                        return;
                    }
                    switch (step.getTool()) {
                        case "createNote" -> {
                            Long noteId = extractNoteIdFromResult(step.getResult());
                            if (noteId != null) {
                                chain.setNoteId(noteId);
                            }
                        }
                        case "extractTodos" -> chain.addTodoIds(collectTodoIdsFromExtractResult(step.getResult()));
                        case "createTodo" -> {
                            Long todoId = extractTodoIdFromResult(step.getResult());
                            if (todoId != null) {
                                chain.addTodoIds(List.of(todoId));
                            }
                        }
                        default -> {
                        }
                    }
                });
        return chain;
    }

    private Long resolveTodoIdForReminder(
            Map<String, Object> payload,
            AgentConfirmChain chain,
            List<AiAgentStepVO> allSteps,
            Long userId
    ) {
        Long todoId = asLong(payload.get("todoId"));
        String todoTitle = payload.get("todoTitle") != null ? String.valueOf(payload.get("todoTitle")).trim() : null;
        if (todoId != null && todoId > 0) {
            return todoId;
        }

        Long matched = findTodoIdByTitleInCompletedSteps(allSteps, todoTitle);
        if (matched != null) {
            return matched;
        }

        todoId = chain.resolveTodoId(null, todoTitle);
        if (todoId != null) {
            return todoId;
        }

        Long noteId = chain.getNoteId() != null ? chain.getNoteId() : findNoteIdFromCompletedSteps(allSteps);
        if (noteId != null && StringUtils.hasText(todoTitle)) {
            todoId = findTodoIdByNoteAndTitle(noteId, todoTitle, userId);
            if (todoId != null) {
                return todoId;
            }
        }

        List<Long> completedTodoIds = collectTodoIdsFromAllCompletedSteps(allSteps);
        if (!completedTodoIds.isEmpty()) {
            return completedTodoIds.get(0);
        }
        return null;
    }

    private List<Long> collectTodoIdsFromAllCompletedSteps(List<AiAgentStepVO> steps) {
        List<Long> ids = new ArrayList<>();
        if (steps == null) {
            return ids;
        }
        for (AiAgentStepVO step : steps) {
            if (!"done".equals(step.getStatus())) {
                continue;
            }
            if ("extractTodos".equals(step.getTool())) {
                ids.addAll(collectTodoIdsFromExtractResult(step.getResult()));
            } else if ("createTodo".equals(step.getTool())) {
                Long todoId = extractTodoIdFromResult(step.getResult());
                if (todoId != null) {
                    ids.add(todoId);
                }
            }
        }
        return ids;
    }

    private List<Long> collectTodoIdsFromExtractResult(Map<String, Object> result) {
        if (result == null || !result.containsKey("extract")) {
            return List.of();
        }
        Object extractObj = result.get("extract");
        if (extractObj instanceof Map<?, ?> extractMap) {
            Object todosObj = extractMap.get("todos");
            return parseTodoIdsFromList(todosObj);
        }
        try {
            NoteExtractTodosVO extract = objectMapper.convertValue(extractObj, NoteExtractTodosVO.class);
            if (extract.getTodos() == null) {
                return List.of();
            }
            return extract.getTodos().stream()
                    .map(TodoVO::getId)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> parseTodoIdsFromList(Object todosObj) {
        if (!(todosObj instanceof List<?> todos)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : todos) {
            if (item instanceof Map<?, ?> todoMap) {
                Long id = asLong(todoMap.get("id"));
                if (id != null) {
                    ids.add(id);
                }
            } else {
                try {
                    TodoVO todo = objectMapper.convertValue(item, TodoVO.class);
                    if (todo.getId() != null) {
                        ids.add(todo.getId());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ids;
    }

    private Long extractTodoIdFromResult(Map<String, Object> result) {
        if (result == null || !result.containsKey("todo")) {
            return null;
        }
        Object todoObj = result.get("todo");
        if (todoObj instanceof Map<?, ?> todoMap) {
            return asLong(todoMap.get("id"));
        }
        try {
            return objectMapper.convertValue(todoObj, TodoVO.class).getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private Long findTodoIdByTitleInCompletedSteps(List<AiAgentStepVO> steps, String todoTitle) {
        if (steps == null || !StringUtils.hasText(todoTitle)) {
            return null;
        }
        String normalized = todoTitle.trim().toLowerCase();
        for (AiAgentStepVO step : steps) {
            if (!"done".equals(step.getStatus())) {
                continue;
            }
            if ("extractTodos".equals(step.getTool())) {
                Long matched = matchTodoIdFromExtractResult(step.getResult(), normalized);
                if (matched != null) {
                    return matched;
                }
            } else if ("createTodo".equals(step.getTool())) {
                Long todoId = extractTodoIdFromResult(step.getResult());
                if (todoId != null && step.getActionPayload() != null) {
                    Object title = step.getActionPayload().get("title");
                    if (title != null && normalized.equals(String.valueOf(title).trim().toLowerCase())) {
                        return todoId;
                    }
                }
            }
        }
        return null;
    }

    private Long matchTodoIdFromExtractResult(Map<String, Object> result, String normalizedTitle) {
        if (result == null || !result.containsKey("extract")) {
            return null;
        }
        Object extractObj = result.get("extract");
        List<TodoVO> todos = new ArrayList<>();
        if (extractObj instanceof Map<?, ?> extractMap) {
            Object todosObj = extractMap.get("todos");
            if (todosObj instanceof List<?> list) {
                for (Object item : list) {
                    try {
                        todos.add(objectMapper.convertValue(item, TodoVO.class));
                    } catch (Exception ignored) {
                    }
                }
            }
        } else {
            try {
                NoteExtractTodosVO extract = objectMapper.convertValue(extractObj, NoteExtractTodosVO.class);
                if (extract.getTodos() != null) {
                    todos.addAll(extract.getTodos());
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        for (TodoVO todo : todos) {
            if (todo.getId() == null || todo.getTitle() == null) {
                continue;
            }
            String title = todo.getTitle().trim().toLowerCase();
            if (title.equals(normalizedTitle) || title.contains(normalizedTitle) || normalizedTitle.contains(title)) {
                return todo.getId();
            }
        }
        return todos.isEmpty() ? null : todos.get(0).getId();
    }

    private Long findTodoIdByNoteAndTitle(Long noteId, String todoTitle, Long userId) {
        List<TodoItemEntity> todos = todoService.lambdaQuery()
                .eq(TodoItemEntity::getNoteId, noteId)
                .eq(TodoItemEntity::getCreatedBy, userId)
                .orderByDesc(TodoItemEntity::getCreatedAt)
                .list();
        String normalized = todoTitle.trim().toLowerCase();
        for (TodoItemEntity todo : todos) {
            if (todo.getTitle() == null) {
                continue;
            }
            String title = todo.getTitle().trim().toLowerCase();
            if (title.equals(normalized) || title.contains(normalized) || normalized.contains(title)) {
                return todo.getId();
            }
        }
        return todos.isEmpty() ? null : todos.get(0).getId();
    }

    private Map<String, Object> executeConfirmedStep(
            AiAgentStepVO step,
            Long userId,
            Long workspaceId,
            AgentConfirmChain chain,
            List<AiAgentStepVO> allSteps
    ) {
        Map<String, Object> payload = step.getActionPayload() != null
                ? new HashMap<>(step.getActionPayload())
                : new HashMap<>();
        if (payload.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "步骤缺少执行参数");
        }
        return switch (step.getTool()) {
            case "createNote" -> {
                NoteVO note = executeCreateNote(payload, userId, workspaceId);
                chain.setNoteId(note.getId());
                yield Map.of("note", note);
            }
            case "createTodo" -> {
                if (payload.get("noteId") == null && chain.getNoteId() != null) {
                    payload.put("noteId", chain.getNoteId());
                }
                TodoVO todo = executeCreateTodo(payload, userId, workspaceId);
                chain.addTodoIds(List.of(todo.getId()));
                yield Map.of("todo", todo);
            }
            case "createReminder" -> {
                Long todoId = resolveTodoIdForReminder(payload, chain, allSteps, userId);
                if (todoId == null) {
                    todoId = createTodoForReminder(payload, userId, workspaceId, chain);
                }
                payload.put("todoId", todoId);
                yield Map.of("reminder", executeCreateReminder(payload, userId, workspaceId));
            }
            case "extractTodos" -> {
                Long noteId = resolveNoteIdForExtract(payload, chain, allSteps);
                if (noteId != null) {
                    payload.put("noteId", noteId);
                    NoteExtractTodosVO extract = executeExtractTodos(payload, userId);
                    if (extract.getTodos() != null) {
                        chain.addTodoIds(extract.getTodos().stream().map(TodoVO::getId).toList());
                    }
                    yield Map.of("extract", extract);
                }
                List<TodoVO> todos = createTodosFromSuggestions(payload, userId, workspaceId, chain);
                yield Map.of(
                        "extract",
                        Map.of(
                                "createdCount", todos.size(),
                                "skippedCount", 0,
                                "todos", todos
                        )
                );
            }
            case "updateNote" -> Map.of("note", executeUpdateNote(payload, userId));
            case "deleteNote" -> {
                noteService.deleteNote(asLong(payload.get("noteId")), userId);
                yield Map.of("deleted", true, "title", payload.get("title"));
            }
            case "updateTodo" -> Map.of("todo", executeUpdateTodo(payload, userId));
            case "completeTodo" -> {
                Long todoId = asLong(payload.get("todoId"));
                yield Map.of("todo", todoService.updateStatus(todoId, 2, userId));
            }
            case "deleteTodo" -> {
                todoService.deleteTodo(asLong(payload.get("todoId")), userId);
                yield Map.of("deleted", true, "title", payload.get("title"));
            }
            case "cancelReminder" -> Map.of(
                    "reminder", reminderService.cancelReminder(asLong(payload.get("reminderId")), userId)
            );
            case "deleteDriveFile" -> {
                attachmentService.delete(asLong(payload.get("attachmentId")), userId);
                yield Map.of("deleted", true, "fileName", payload.get("fileName"));
            }
            case "moveDriveFile" -> Map.of(
                    "attachment", attachmentService.moveToFolder(asLong(payload.get("attachmentId")), asLong(payload.get("folderId")), userId)
            );
            case "linkFileToNote" -> Map.of(
                    "attachment", attachmentService.linkToNote(asLong(payload.get("attachmentId")), asLong(payload.get("noteId")), userId)
            );
            case "createNoteShare" -> Map.of(
                    "share", shareLinkService.createNoteShare(asLong(payload.get("noteId")), buildShareRequest(payload), userId)
            );
            case "createFileShare" -> Map.of(
                    "share", shareLinkService.createAttachmentShare(asLong(payload.get("attachmentId")), buildShareRequest(payload), userId)
            );
            case "revokeShare" -> {
                if (payload.get("token") != null) {
                    shareLinkService.revokeShareByToken(String.valueOf(payload.get("token")), userId);
                } else if (payload.get("noteId") != null) {
                    shareLinkService.revokeNoteShare(asLong(payload.get("noteId")), userId);
                } else {
                    shareLinkService.revokeAttachmentShare(asLong(payload.get("attachmentId")), userId);
                }
                yield Map.of("revoked", true);
            }
            case "createTag" -> Map.of("tag", executeCreateTag(payload, workspaceId));
            case "deleteTag" -> {
                tagService.removeById(asLong(payload.get("tagId")));
                yield Map.of("deleted", true, "tagName", payload.get("tagName"));
            }
            case "tagNote" -> {
                noteTagService.syncNoteTags(
                        asLong(payload.get("noteId")),
                        workspaceId,
                        objectMapper.convertValue(payload.get("tagIds"), new TypeReference<List<Long>>() {})
                );
                yield Map.of("tagged", true, "noteId", payload.get("noteId"));
            }
            case "createNoteFolder" -> Map.of("folder", executeCreateNoteFolder(payload, workspaceId));
            case "createDriveFolder" -> Map.of("folder", executeCreateDriveFolder(payload, userId));
            case "moveNoteToFolder" -> Map.of("note", executeUpdateNote(payload, userId));
            default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持确认的工具：" + step.getTool());
        };
    }

    private TodoVO executeUpdateTodo(Map<String, Object> payload, Long userId) {
        Long todoId = asLong(payload.get("todoId"));
        TodoUpdateRequest request = new TodoUpdateRequest();
        request.setTitle(String.valueOf(payload.get("title")));
        request.setHorizon(String.valueOf(payload.getOrDefault("horizon", "action")));
        request.setPriority(payload.get("priority") instanceof Number number ? number.intValue() : 2);
        if (payload.get("dueAt") != null) {
            request.setDueAt(com.noto.zhihui.common.util.ExtractedTodoDueHelper.parseDueAt(String.valueOf(payload.get("dueAt"))));
        }
        TodoVO updated = todoService.updateTodo(todoId, request, userId);
        if (payload.get("status") instanceof Number status) {
            updated = todoService.updateStatus(todoId, status.intValue(), userId);
        }
        return updated;
    }

    private NoteVO executeUpdateNote(Map<String, Object> payload, Long userId) {
        Long noteId = asLong(payload.get("noteId"));
        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setTitle(String.valueOf(payload.get("title")));
        request.setContent(payload.get("content") != null ? String.valueOf(payload.get("content")) : "");
        request.setContentType(payload.get("contentType") != null ? String.valueOf(payload.get("contentType")) : "markdown");
        if (payload.containsKey("folderId")) {
            request.setFolderId(asLong(payload.get("folderId")));
        }
        if (payload.get("tagIds") != null) {
            request.setTagIds(objectMapper.convertValue(payload.get("tagIds"), new TypeReference<List<Long>>() {}));
        }
        return noteService.updateNote(noteId, request, userId);
    }

    private TagEntity executeCreateTag(Map<String, Object> payload, Long workspaceId) {
        TagEntity tag = new TagEntity();
        tag.setWorkspaceId(workspaceId);
        tag.setName(String.valueOf(payload.get("name")).trim());
        Object color = payload.get("color");
        tag.setColor(color != null && StringUtils.hasText(String.valueOf(color)) ? String.valueOf(color).trim() : "#1677ff");
        tagService.save(tag);
        return tag;
    }

    private NoteFolderEntity executeCreateNoteFolder(Map<String, Object> payload, Long workspaceId) {
        NoteFolderEntity folder = new NoteFolderEntity();
        folder.setWorkspaceId(workspaceId);
        folder.setParentId(asLong(payload.get("parentId")));
        folder.setName(String.valueOf(payload.get("name")).trim());
        folder.setSortOrder(0);
        noteFolderService.save(folder);
        return folder;
    }

    private com.noto.zhihui.vo.drive.DriveFolderVO executeCreateDriveFolder(Map<String, Object> payload, Long userId) {
        DriveFolderCreateRequest request = new DriveFolderCreateRequest();
        request.setWorkspaceId(asLong(payload.get("workspaceId")));
        request.setParentId(asLong(payload.get("parentId")));
        request.setName(String.valueOf(payload.get("name")).trim());
        return driveFolderService.createFolder(request, userId);
    }

    private ShareCreateRequest buildShareRequest(Map<String, Object> payload) {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setExpiresInDays(asInteger(payload.get("expiresInDays")));
        Object password = payload.get("password");
        request.setPassword(password != null ? String.valueOf(password) : null);
        return request;
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

    private NoteVO executeCreateNote(Map<String, Object> payload, Long userId, Long workspaceId) {
        NoteCreateRequest request = new NoteCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setTitle(String.valueOf(payload.get("title")));
        request.setContent(payload.get("content") != null ? String.valueOf(payload.get("content")) : "");
        request.setContentType(payload.get("contentType") != null ? String.valueOf(payload.get("contentType")) : "markdown");
        if (payload.get("folderId") != null) {
            request.setFolderId(asLong(payload.get("folderId")));
        }
        return noteService.createNote(request, userId);
    }

    private Long createTodoForReminder(
            Map<String, Object> payload,
            Long userId,
            Long workspaceId,
            AgentConfirmChain chain
    ) {
        String todoTitle = payload.get("todoTitle") != null ? String.valueOf(payload.get("todoTitle")).trim() : null;
        if (!StringUtils.hasText(todoTitle) && payload.get("message") != null) {
            todoTitle = String.valueOf(payload.get("message")).trim();
        }
        if (!StringUtils.hasText(todoTitle)) {
            todoTitle = "待办";
        }
        Map<String, Object> todoPayload = new HashMap<>();
        todoPayload.put("title", todoTitle);
        todoPayload.put("horizon", "action");
        todoPayload.put("priority", 2);
        Object triggerAt = payload.get("triggerAt");
        if (triggerAt != null && StringUtils.hasText(String.valueOf(triggerAt))) {
            todoPayload.put("dueAt", String.valueOf(triggerAt));
        }
        TodoVO todo = executeCreateTodo(todoPayload, userId, workspaceId);
        chain.addTodoIds(List.of(todo.getId()));
        return todo.getId();
    }

    private List<TodoVO> createTodosFromSuggestions(
            Map<String, Object> payload,
            Long userId,
            Long workspaceId,
            AgentConfirmChain chain
    ) {
        List<ExtractedTodoSuggestionVO> suggestions = objectMapper.convertValue(
                payload.get("suggestions"),
                new TypeReference<List<ExtractedTodoSuggestionVO>>() {
                }
        );
        List<TodoVO> created = new ArrayList<>();
        if (suggestions == null) {
            return created;
        }
        for (ExtractedTodoSuggestionVO suggestion : suggestions) {
            if (suggestion.isDuplicate()) {
                continue;
            }
            Map<String, Object> todoPayload = new HashMap<>();
            todoPayload.put("title", suggestion.getTitle());
            todoPayload.put("horizon", suggestion.getHorizon() != null ? suggestion.getHorizon() : "action");
            todoPayload.put("priority", suggestion.getPriority() > 0 ? suggestion.getPriority() : 2);
            if (suggestion.getDueAt() != null) {
                todoPayload.put("dueAt", suggestion.getDueAt().toString());
            }
            TodoVO todo = executeCreateTodo(todoPayload, userId, workspaceId);
            created.add(todo);
            chain.addTodoIds(List.of(todo.getId()));
        }
        return created;
    }

    private Long resolveNoteIdForExtract(
            Map<String, Object> payload,
            AgentConfirmChain chain,
            List<AiAgentStepVO> allSteps
    ) {
        Long noteId = asLong(payload.get("noteId"));
        if (noteId != null) {
            return noteId;
        }
        if (chain.getNoteId() != null) {
            return chain.getNoteId();
        }
        return findNoteIdFromCompletedSteps(allSteps);
    }

    private Long findNoteIdFromCompletedSteps(List<AiAgentStepVO> steps) {
        if (steps == null) {
            return null;
        }
        for (AiAgentStepVO step : steps) {
            if (!"createNote".equals(step.getTool()) || !"done".equals(step.getStatus())) {
                continue;
            }
            Long noteId = extractNoteIdFromResult(step.getResult());
            if (noteId != null) {
                return noteId;
            }
        }
        return null;
    }

    private Long extractNoteIdFromResult(Map<String, Object> result) {
        if (result == null || !result.containsKey("note")) {
            return null;
        }
        Object noteObj = result.get("note");
        if (noteObj instanceof Map<?, ?> noteMap) {
            return asLong(noteMap.get("id"));
        }
        try {
            NoteVO note = objectMapper.convertValue(noteObj, NoteVO.class);
            return note.getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private TodoVO executeCreateTodo(Map<String, Object> payload, Long userId, Long workspaceId) {
        TodoCreateRequest request = new TodoCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setNoteId(asLong(payload.get("noteId")));
        request.setTitle(String.valueOf(payload.get("title")));
        request.setHorizon(payload.get("horizon") != null ? String.valueOf(payload.get("horizon")) : "action");
        request.setPriority(payload.get("priority") != null ? ((Number) payload.get("priority")).intValue() : 2);
        Object dueAt = payload.get("dueAt");
        if (dueAt != null && StringUtils.hasText(String.valueOf(dueAt))) {
            request.setDueAt(LocalDateTime.parse(String.valueOf(dueAt)));
        }
        return todoService.createTodo(request, userId);
    }

    private ReminderVO executeCreateReminder(Map<String, Object> payload, Long userId, Long workspaceId) {
        ReminderCreateRequest request = new ReminderCreateRequest();
        request.setWorkspaceId(workspaceId);
        request.setTodoId(asLong(payload.get("todoId")));
        request.setTriggerAt(LocalDateTime.parse(String.valueOf(payload.get("triggerAt"))));
        request.setMessage(payload.get("message") != null ? String.valueOf(payload.get("message")) : null);
        request.setReminderType(payload.get("reminderType") != null ? String.valueOf(payload.get("reminderType")) : "due");
        return reminderService.createReminder(request, userId);
    }

    private NoteExtractTodosVO executeExtractTodos(Map<String, Object> payload, Long userId) {
        Long noteId = asLong(payload.get("noteId"));
        if (noteId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "提取待办缺少 noteId");
        }
        List<ExtractedTodoSuggestionVO> suggestions = objectMapper.convertValue(
                payload.get("suggestions"),
                new TypeReference<List<ExtractedTodoSuggestionVO>>() {
                }
        );
        ConfirmExtractTodosRequest request = new ConfirmExtractTodosRequest();
        List<ConfirmExtractTodoItem> items = new ArrayList<>();
        if (suggestions != null) {
            for (ExtractedTodoSuggestionVO suggestion : suggestions) {
                if (suggestion.isDuplicate()) {
                    continue;
                }
                ConfirmExtractTodoItem item = new ConfirmExtractTodoItem();
                item.setTitle(suggestion.getTitle());
                item.setCompleted(suggestion.isCompleted());
                item.setPriority(suggestion.getPriority());
                item.setHorizon(suggestion.getHorizon());
                item.setDueAt(suggestion.getDueAt());
                items.add(item);
            }
        }
        request.setItems(items);
        return aiService.confirmExtractTodosFromNote(noteId, userId, request);
    }

    private AiTaskEntity requireOwnedTask(Long taskId, Long userId) {
        AiTaskEntity task = aiTaskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return task;
    }

    private AgentTaskPayload buildPayload(String instruction, String reply, List<AiAgentStepVO> steps) {
        AgentTaskPayload payload = new AgentTaskPayload();
        payload.setInstruction(instruction);
        payload.setAssistantReply(AgentReplyComposer.compose(reply, steps, objectMapper));
        payload.setSteps(steps == null ? List.of() : new ArrayList<>(steps));
        return payload;
    }

    private boolean hasPendingConfirm(List<AiAgentStepVO> steps) {
        if (steps == null) {
            return false;
        }
        return steps.stream().anyMatch(step -> "pending_confirm".equals(step.getStatus()));
    }

    private AgentTaskPayload readPayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new AgentTaskPayload();
        }
        try {
            return objectMapper.readValue(raw, AgentTaskPayload.class);
        } catch (Exception ex) {
            AgentTaskPayload fallback = new AgentTaskPayload();
            fallback.setAssistantReply(raw);
            return fallback;
        }
    }

    private String writePayload(AgentTaskPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "任务结果序列化失败");
        }
    }

    private AiAgentTaskVO toVO(AiTaskEntity entity, AgentTaskPayload payload) {
        AiAgentTaskVO vo = new AiAgentTaskVO();
        vo.setId(entity.getId());
        vo.setWorkspaceId(entity.getWorkspaceId());
        vo.setTaskType(entity.getTaskType());
        vo.setStatus(entity.getStatus());
        vo.setInstruction(payload.getInstruction() != null ? payload.getInstruction() : entity.getInputContent());
        vo.setAssistantReply(payload.getAssistantReply());
        vo.setSteps(payload.getSteps());
        vo.setAutoExecuted(payload.getAutoExecuted());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String resolveConversationBlock(AiAgentPlanRequest request, Long userId) {
        String sessionBlock = request.getSessionId() == null
                ? ""
                : aiChatSessionService.buildRecentDialogBlock(request.getSessionId(), userId, 8);
        return AiConversationContext.resolveContextBlock(request.getRecentContext(), sessionBlock);
    }

    private String preview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static final class AgentTaskPayload {
        private String instruction;
        private String assistantReply;
        private List<AiAgentStepVO> steps;
        private Boolean autoExecuted;

        public String getInstruction() {
            return instruction;
        }

        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }

        public String getAssistantReply() {
            return assistantReply;
        }

        public void setAssistantReply(String assistantReply) {
            this.assistantReply = assistantReply;
        }

        public List<AiAgentStepVO> getSteps() {
            return steps;
        }

        public void setSteps(List<AiAgentStepVO> steps) {
            this.steps = steps;
        }

        public Boolean getAutoExecuted() {
            return autoExecuted;
        }

        public void setAutoExecuted(Boolean autoExecuted) {
            this.autoExecuted = autoExecuted;
        }
    }
}
