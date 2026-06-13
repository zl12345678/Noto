package com.noto.zhihui.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.AiConversationContext;
import com.noto.zhihui.common.util.ExtractedTodoDueHelper;
import com.noto.zhihui.common.util.NoteContentChunkSelector;
import com.noto.zhihui.common.util.NoteChunkSplitter;
import com.noto.zhihui.common.util.MarkdownTodoExtractor.ExtractedTodoItem;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.dto.ai.AiAskRequest;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.ai.NoteAiDraftRequest;
import com.noto.zhihui.dto.ai.NoteSelectionTransformRequest;
import com.noto.zhihui.dto.ai.NoteSynthesizeRequest;
import com.noto.zhihui.dto.ai.NoteTransformRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.service.AiAskContext;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.AiStreamSink;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.vo.ai.AiAskVO;
import com.noto.zhihui.vo.ai.AiReferenceVO;
import com.noto.zhihui.vo.ai.AiStatusVO;
import com.noto.zhihui.vo.ai.NoteSummaryVO;
import com.noto.zhihui.vo.ai.NoteSynthesizeSourceVO;
import com.noto.zhihui.vo.ai.NoteSynthesizeVO;
import com.noto.zhihui.vo.ai.NoteTransformVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.ai.TodoBreakdownRequest;
import com.noto.zhihui.dto.ai.TodoCompletionRetroRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.service.NoteRetrievalService;
import com.noto.zhihui.service.NoteRetrievalService.RetrievedNoteChunk;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionsVO;
import com.noto.zhihui.vo.ai.AiSubtaskSuggestionVO;
import com.noto.zhihui.vo.ai.TodoBreakdownVO;
import com.noto.zhihui.vo.ai.TodoCompletionRetroVO;
import com.noto.zhihui.vo.ai.TodoOverdueAdviceItemVO;
import com.noto.zhihui.vo.ai.TodoOverdueAdviceVO;
import com.noto.zhihui.vo.todo.TodoBoardVO;
import com.noto.zhihui.vo.todo.TodoVO;
import com.noto.zhihui.vo.note.NoteVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AiServiceImpl implements AiService {

    private static final int MAX_NOTE_CHARS = 8000;
    private static final int MAX_CHUNKED_MATERIAL_CHARS = 16_000;
    private static final int MAX_SYNTHESIS_NOTES = 8;
    private static final int MAX_SYNTHESIS_CHARS_PER_NOTE = 2000;
    private static final int PROMPT_PREVIEW_CHARS = 240;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final NotoAiProperties aiProperties;
    private final NoteService noteService;
    private final NoteRetrievalService noteRetrievalService;
    private final TodoService todoService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UserSettingService userSettingService;
    private final WorkspaceService workspaceService;
    private final AiChatSessionService aiChatSessionService;

    public AiServiceImpl(
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            NotoAiProperties aiProperties,
            NoteService noteService,
            NoteRetrievalService noteRetrievalService,
            TodoService todoService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            UserSettingService userSettingService,
            WorkspaceService workspaceService,
            AiChatSessionService aiChatSessionService
    ) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.aiProperties = aiProperties;
        this.noteService = noteService;
        this.noteRetrievalService = noteRetrievalService;
        this.todoService = todoService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.userSettingService = userSettingService;
        this.workspaceService = workspaceService;
        this.aiChatSessionService = aiChatSessionService;
    }

    @Override
    public AiStatusVO status() {
        return new AiStatusVO(
                true,
                StringUtils.hasText(aiProperties.getApiKey()),
                aiProperties.getModel(),
                "langchain4j-dashscope"
        );
    }

    @Override
    public NoteSummaryVO summarizeNote(Long noteId, Long userId, NoteAiDraftRequest draft) {
        ensureConfigured();
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        String title = resolveDraftTitle(draft, note.getTitle());
        String content = resolveDraftContent(draft, note.getContent());
        String prompt = """
                你是知识笔记助手。请阅读以下 Markdown 文档，用中文输出 JSON，字段如下：
                {"summary":"150字以内摘要","keyPoints":["要点1","要点2"],"riskPoints":["风险或待关注1"]}
                要求：仅输出 JSON，不要 markdown 代码块，不要额外解释。

                标题：%s
                正文：
                %s
                """.formatted(title, truncate(content));

        return parseSummary(
                callModel(prompt, userId, note.getWorkspaceId(), "ai.summarize", "note", noteId),
                title
        );
    }

    @Override
    public NoteTransformVO transformNote(Long noteId, Long userId, NoteTransformRequest request) {
        ensureConfigured();
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        String mode = request.getMode() == null ? "" : request.getMode().trim().toLowerCase();
        String title = resolveDraftTitle(request, note.getTitle());
        String content = resolveDraftContent(request, note.getContent());
        if (!StringUtils.hasText(content) || "（空文档）".equals(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文档内容为空，无法处理");
        }

        String prompt = buildTransformPrompt(mode, title, content);

        String transformed = callModel(
                prompt, userId, note.getWorkspaceId(), "ai.transform", "note", noteId
        );
        return new NoteTransformVO(mode, stripMarkdownFence(transformed));
    }

    @Override
    public void transformSelectionStream(
            Long noteId,
            NoteSelectionTransformRequest request,
            Long userId,
            AiStreamSink sink
    ) {
        ensureConfigured();
        long startedAt = System.currentTimeMillis();
        try {
            NoteEntity note = noteService.requireOwnedNote(noteId, userId);
            String mode = request.getMode() == null ? "" : request.getMode().trim().toLowerCase();
            String selected = request.getSelectedText() == null ? "" : request.getSelectedText().trim();
            if (!StringUtils.hasText(selected)) {
                sink.fail("请先选中要处理的段落");
                return;
            }
            String title = resolveDraftTitle(request, note.getTitle());
            String prompt = buildSelectionTransformPrompt(mode, title, selected);
            streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
                private final StringBuilder builder = new StringBuilder();

                @Override
                public void onPartialResponse(String partialResponse) {
                    if (StringUtils.hasText(partialResponse)) {
                        builder.append(partialResponse);
                        sink.sendToken(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    String result = builder.toString();
                    if (!StringUtils.hasText(result) && completeResponse != null && completeResponse.aiMessage() != null) {
                        result = completeResponse.aiMessage().text();
                    }
                    recordAudit(
                            userId,
                            note.getWorkspaceId(),
                            "ai.transform.selection",
                            "note",
                            noteId,
                            prompt,
                            result,
                            true,
                            null,
                            System.currentTimeMillis() - startedAt
                    );
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    recordAudit(
                            userId,
                            note.getWorkspaceId(),
                            "ai.transform.selection",
                            "note",
                            noteId,
                            prompt,
                            null,
                            false,
                            error.getMessage(),
                            System.currentTimeMillis() - startedAt
                    );
                    sink.fail("流式改写失败：" + error.getMessage());
                }
            });
        } catch (BizException ex) {
            sink.fail(ex.getMessage());
        } catch (Exception ex) {
            sink.fail("选中改写失败：" + ex.getMessage());
        }
    }

    @Override
    public AiAskVO ask(AiAskRequest request, Long userId) {
        ensureConfigured();
        AiAskContext context = buildAskContext(request, userId);
        ParsedAnswer parsed = parseAnswerWithGaps(callModel(
                context.getPrompt(),
                userId,
                request.getWorkspaceId(),
                "ai.ask",
                "note".equalsIgnoreCase(request.getScope()) ? "note" : "workspace",
                "note".equalsIgnoreCase(request.getScope()) ? request.getTargetId() : request.getWorkspaceId()
        ));
        AiAskVO vo = new AiAskVO();
        vo.setAnswer(parsed.answer());
        vo.setKnowledgeGaps(mergeKnowledgeGaps(parsed.knowledgeGaps(), context.getRetrievalGaps()));
        vo.setReferences(context.getReferences());
        return vo;
    }

    @Override
    public void askStream(AiAskRequest request, Long userId, AiStreamSink sink) {
        ensureConfigured();
        long startedAt = System.currentTimeMillis();
        try {
            AiAskContext context = buildAskContext(request, userId);
            String prompt = context.getPrompt();
            sink.sendReferences(context.getReferences());
            streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
                private final StringBuilder answerBuilder = new StringBuilder();

                @Override
                public void onPartialResponse(String partialResponse) {
                    if (StringUtils.hasText(partialResponse)) {
                        answerBuilder.append(partialResponse);
                        sink.sendToken(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    String answer = answerBuilder.toString();
                    if (!StringUtils.hasText(answer) && completeResponse != null && completeResponse.aiMessage() != null) {
                        answer = completeResponse.aiMessage().text();
                    }
                    ParsedAnswer parsed = parseAnswerWithGaps(answer);
                    List<String> gaps = mergeKnowledgeGaps(parsed.knowledgeGaps(), context.getRetrievalGaps());
                    if (!gaps.isEmpty()) {
                        sink.sendKnowledgeGaps(gaps);
                    }
                    recordAudit(
                            userId,
                            request.getWorkspaceId(),
                            "ai.ask.stream",
                            "note".equalsIgnoreCase(request.getScope()) ? "note" : "workspace",
                            "note".equalsIgnoreCase(request.getScope()) ? request.getTargetId() : request.getWorkspaceId(),
                            prompt,
                            parsed.answer(),
                            true,
                            null,
                            System.currentTimeMillis() - startedAt
                    );
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    recordAudit(
                            userId,
                            request.getWorkspaceId(),
                            "ai.ask.stream",
                            "note".equalsIgnoreCase(request.getScope()) ? "note" : "workspace",
                            "note".equalsIgnoreCase(request.getScope()) ? request.getTargetId() : request.getWorkspaceId(),
                            prompt,
                            null,
                            false,
                            error.getMessage(),
                            System.currentTimeMillis() - startedAt
                    );
                    sink.fail("LangChain4j 流式调用失败：" + error.getMessage());
                }
            });
        } catch (BizException ex) {
            sink.fail(ex.getMessage());
        } catch (Exception ex) {
            sink.fail("流式问答失败：" + ex.getMessage());
        }
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractTodosFromNote(Long noteId, Long userId) {
        ensureConfigured();
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        String referenceDate = note.getLastEditedAt() != null
                ? note.getLastEditedAt().toLocalDate().toString()
                : LocalDate.now().toString();
        List<ExtractedTodoItem> extracted = extractTodosFromMarkdown(
                note.getTitle(),
                note.getContent(),
                referenceDate,
                userId,
                note.getWorkspaceId(),
                noteId
        );
        return todoService.previewExtractedTodos(noteId, userId, extracted);
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractTodosFromContent(
            String title,
            String content,
            Long workspaceId,
            Long userId
    ) {
        ensureConfigured();
        String noteTitle = StringUtils.hasText(title) ? title.trim() : "未命名文档";
        String noteContent = content == null ? "" : content;
        List<ExtractedTodoItem> extracted = extractTodosFromMarkdown(
                noteTitle,
                noteContent,
                LocalDate.now().toString(),
                userId,
                workspaceId,
                null
        );
        return todoService.previewExtractedTodosFromDraft(noteTitle, extracted);
    }

    private List<ExtractedTodoItem> extractTodosFromMarkdown(
            String title,
            String content,
            String referenceDate,
            Long userId,
            Long workspaceId,
            Long noteId
    ) {
        String prompt = """
                你是待办提取助手。请阅读以下 Markdown 文档，识别可执行的待办任务。
                输出 JSON，格式如下：
                {"todos":[{"title":"任务标题","completed":false,"priority":2,"horizon":"action","dueAt":null}]}
                规则：
                1. 仅提取明确、可执行的任务，不要臆造
                2. completed 表示文档中是否已完成
                3. priority 取值 1(高)/2(中)/3(低)，默认 2
                4. horizon 取值 action(短期可执行) 或 long_term(长期目标)，默认 action
                5. dueAt 规则（重要）：
                   - 仅当文档中有明确期限/日期/相对时间（如「6月10日」「本周五」「今晚8点」「明天前」）时才填写 ISO 本地时间
                   - 相对时间请基于「文档参考日 %s」与「今天 %s」综合推断
                   - 无法从文档推断截止日时 dueAt 必须为 null，禁止按优先级编造「今日18:00/明日18:00」等默认值
                   - 只有日期无时刻时可写 YYYY-MM-DD；有具体时刻则写 YYYY-MM-DDTHH:mm:ss
                6. 若无待办，返回 {"todos":[]}
                7. 仅输出 JSON，不要 markdown 代码块，不要额外解释

                标题：%s
                正文：
                %s
                """.formatted(referenceDate, LocalDate.now(), title, materialForLongDocument(content, title));

        return parseExtractedTodos(callModel(
                prompt, userId, workspaceId, "ai.extract_todos", "note", noteId
        ));
    }

    @Override
    public NoteExtractTodosVO confirmExtractTodosFromNote(Long noteId, Long userId, ConfirmExtractTodosRequest request) {
        return todoService.confirmExtractTodosFromNote(noteId, userId, request);
    }

    @Override
    public AiDailySuggestionsVO suggestDailyActions(Long userId, Long workspaceId) {
        ensureConfigured();
        TodoBoardVO board = todoService.getTodoBoard(userId, workspaceId);
        long overdueCount = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .isNotNull(TodoItemEntity::getDueAt)
                .lt(TodoItemEntity::getDueAt, LocalDateTime.now())
                .count();

        String memoryBlock = userSettingService.resolveLightMemoryPromptBlock(userId);
        String memorySection = StringUtils.hasText(memoryBlock) ? memoryBlock + "\n\n" : "";
        String prompt = """
                你是行动规划助手。根据用户当前待办看板，推荐今天最值得优先处理的 3 项（必须从给定列表中选，不可编造 id）。
                输出 JSON：
                {"summary":"一句话今日重点","suggestions":[{"todoId":1,"title":"任务标题","reason":"推荐理由","action":"start"}]}
                action 取值：start（从队列开做）、continue（继续推进进行中）、focus（今日重点跟进）。
                优先：逾期 > 高优先级 > 进行中卡住 > 队列前排。
                若用户有主攻项目记忆，优先推荐与主攻项目相关的待办。

                %s逾期待办数：%d
                待办队列：
                %s
                进行中：
                %s
                """.formatted(
                memorySection,
                overdueCount,
                formatTodosForPrompt(board.getActionTodos()),
                formatTodosForPrompt(board.getParallelTodos())
        );

        Set<Long> allowedIds = new HashSet<>();
        board.getActionTodos().forEach(item -> allowedIds.add(item.getId()));
        board.getParallelTodos().forEach(item -> allowedIds.add(item.getId()));
        return parseDailySuggestions(
                callModel(prompt, userId, workspaceId, "ai.daily_suggestions", "workspace", workspaceId),
                allowedIds
        );
    }

    @Override
    public AiDailyReviewVO reviewDailyProgress(Long userId, Long workspaceId) {
        ensureConfigured();
        List<TodoVO> completed = todoService.listTodayCompletedTodos(userId, workspaceId, 12);
        TodoBoardVO board = todoService.getTodoBoard(userId, workspaceId);
        long overdueCount = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .isNotNull(TodoItemEntity::getDueAt)
                .lt(TodoItemEntity::getDueAt, LocalDateTime.now())
                .count();

        String prompt = """
                你是个人效能复盘助手。根据今日完成与当前待办，生成日复盘 JSON：
                {"summary":"今日整体评价","highlights":["完成亮点1"],"blockers":["卡点或未完成原因"],"tomorrowFocus":["明日建议1"]}
                要求：highlights/blockers/tomorrowFocus 各 1-3 条；若今日无完成，highlights 可写「今日暂无完成项」；仅输出 JSON。

                逾期待办数：%d
                今日完成：
                %s
                待办队列：
                %s
                进行中：
                %s
                """.formatted(
                overdueCount,
                formatCompletedForReview(completed),
                formatTodosForPrompt(board.getActionTodos()),
                formatTodosForPrompt(board.getParallelTodos())
        );

        AiDailyReviewVO review = parseDailyReview(callModel(
                prompt, userId, workspaceId, "ai.daily_review", "workspace", workspaceId
        ));
        review.setCompletedCount(todoService.countTodayCompletedTodos(userId));
        return review;
    }

    @Override
    public TodoBreakdownVO breakdownTodo(TodoBreakdownRequest request, Long userId) {
        ensureConfigured();
        String horizon = request.getHorizon() == null ? "action" : request.getHorizon().trim();
        String prompt = """
                你是任务拆解助手。将以下目标拆解为 3-6 个可执行子任务。
                输出 JSON：
                {"subtasks":[{"title":"子任务","priority":2,"horizon":"action","dueAt":null}]}
                规则：priority 1高/2中/3低；horizon 为 action 或 long_term；dueAt 仅在有明确期限时填写，否则 null；仅输出 JSON。

                目标标题：%s
                补充说明：%s
                默认类型：%s
                """.formatted(
                request.getTitle().trim(),
                request.getDescription() == null ? "（无）" : request.getDescription().trim(),
                horizon
        );
        List<AiSubtaskSuggestionVO> subtasks = parseSubtasks(callModel(
                prompt, userId, null, "ai.todo_breakdown", "todo", null
        ));
        return new TodoBreakdownVO(request.getTitle().trim(), subtasks);
    }

    @Override
    public NoteSynthesizeVO synthesizeNotes(NoteSynthesizeRequest request, Long userId) {
        ensureConfigured();
        Long workspaceId = request.getWorkspaceId();
        workspaceService.requireOwnedWorkspace(workspaceId, userId);

        String topic = request.getTopic().trim();
        String template = request.getTemplate().trim().toLowerCase();
        List<NoteEntity> sourceNotes = collectSynthesisSources(request, userId, workspaceId, topic);
        if (sourceNotes.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "未找到可合成的文档，请调整主题或时间范围");
        }

        String material = buildSynthesisMaterial(sourceNotes);
        String prompt = buildSynthesisPrompt(template, topic, material, request.getDateFrom(), request.getDateTo());
        String synthesized = stripMarkdownFence(callModel(
                prompt, userId, workspaceId, "ai.synthesize", "workspace", workspaceId
        ));

        String title = buildSynthesisTitle(template, topic);
        String contentWithSources = appendSourceSection(synthesized, sourceNotes);

        NoteSynthesizeVO vo = new NoteSynthesizeVO();
        vo.setTemplate(template);
        vo.setTitle(title);
        vo.setContent(contentWithSources);
        vo.setSources(sourceNotes.stream()
                .map(note -> new NoteSynthesizeSourceVO(note.getId(), note.getTitle()))
                .toList());

        boolean saveAsNote = request.getSaveAsNote() == null || request.getSaveAsNote();
        if (saveAsNote) {
            NoteCreateRequest createRequest = new NoteCreateRequest();
            createRequest.setWorkspaceId(workspaceId);
            createRequest.setFolderId(request.getFolderId());
            createRequest.setTitle(title);
            createRequest.setContent(contentWithSources);
            NoteVO created = noteService.createNote(createRequest, userId);
            vo.setCreatedNoteId(created.getId());
        }
        return vo;
    }

    @Override
    public TodoOverdueAdviceVO suggestOverdueTodoActions(Long userId, Long workspaceId) {
        ensureConfigured();
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        List<TodoVO> overdueTodos = listOverdueTodos(userId, workspaceId, 12);
        if (overdueTodos.isEmpty()) {
            return new TodoOverdueAdviceVO("当前没有逾期待办", List.of());
        }

        Set<Long> allowedIds = overdueTodos.stream().map(TodoVO::getId).collect(Collectors.toSet());
        String prompt = """
                你是待办处理助手。用户有以下逾期待办，请为每项给出处理建议。
                输出 JSON：
                {"summary":"一句话整体建议","suggestions":[{"todoId":1,"title":"任务标题","action":"reschedule","reason":"建议理由","suggestedDueAt":"2026-06-10T09:00:00"}]}
                action 取值：
                - reschedule：改期（给出 suggestedDueAt，ISO 日期时间）
                - breakdown：任务太大，建议拆成子任务
                - archive：不再 relevant，建议归档取消
                - complete：其实已完成或可以立刻勾掉
                规则：必须从下列列表选 todoId，不可编造；每项一条建议；优先给出可执行建议。

                逾期待办：
                %s
                """.formatted(formatOverdueTodosForPrompt(overdueTodos));

        return parseOverdueAdvice(
                callModel(prompt, userId, workspaceId, "ai.todo_overdue_advice", "workspace", workspaceId),
                allowedIds
        );
    }

    @Override
    public TodoCompletionRetroVO completionRetro(Long todoId, TodoCompletionRetroRequest request, Long userId) {
        ensureConfigured();
        TodoItemEntity todo = todoService.getById(todoId);
        if (todo == null || !userId.equals(todo.getCreatedBy())) {
            throw new BizException(ErrorCode.TODO_NOT_FOUND);
        }

        NoteEntity note = null;
        if (todo.getNoteId() != null) {
            note = noteService.requireOwnedNote(todo.getNoteId(), userId);
        }

        String line = request.getLine();
        if (!StringUtils.hasText(line)) {
            line = generateCompletionRetroLine(todo, note, userId, request.getScenario());
        } else {
            line = line.trim();
        }

        boolean appended = false;
        String noteTitle = note != null ? note.getTitle() : null;
        if (Boolean.TRUE.equals(request.getAppend())) {
            if (note == null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "该待办未关联笔记，无法写入复盘");
            }
            appendRetroToNote(note, todo.getTitle(), line, userId);
            appended = true;
        }

        return new TodoCompletionRetroVO(line, todo.getNoteId(), noteTitle, todo.getTitle(), appended);
    }

    private String generateCompletionRetroLine(TodoItemEntity todo, NoteEntity note, Long userId, String scenario) {
        String noteBlock = "（无关联文档）";
        if (note != null) {
            String ctx = StringUtils.hasText(note.getSummary())
                    ? note.getSummary()
                    : NoteTextUtils.excerpt(note.getContent(), 500);
            noteBlock = "关联文档「" + note.getTitle() + "」上下文：\n" + ctx;
        }
        String prompt;
        if ("reminder_due".equalsIgnoreCase(scenario)) {
            prompt = """
                    待办「%s」的提醒刚刚触发，用户希望记录一句进展。
                    %s
                    请用一句话写当前进展或下一步（30字以内，不含引号），仅输出纯文本。
                    """.formatted(todo.getTitle(), noteBlock);
        } else {
            prompt = """
                    用户刚完成了待办「%s」。
                    %s
                    请用一句话写完成复盘（陈述结果或收获，30字以内，不含引号），仅输出纯文本。
                    """.formatted(todo.getTitle(), noteBlock);
        }
        return stripMarkdownFence(callModel(
                prompt, userId, todo.getWorkspaceId(), "ai.todo_completion_retro", "todo", todo.getId()
        )).trim();
    }

    private void appendRetroToNote(NoteEntity note, String todoTitle, String line, Long userId) {
        String stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String block = "\n\n---\n\n**" + stamp + " 完成复盘 · " + todoTitle + "**\n\n> " + line + "\n";
        NoteUpdateRequest update = new NoteUpdateRequest();
        update.setTitle(note.getTitle());
        String content = note.getContent() == null ? "" : note.getContent();
        update.setContent(content + block);
        noteService.updateNote(note.getId(), update, userId);
    }

    private List<TodoVO> listOverdueTodos(Long userId, Long workspaceId, int limit) {
        return todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .isNotNull(TodoItemEntity::getDueAt)
                .lt(TodoItemEntity::getDueAt, LocalDateTime.now())
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .orderByAsc(TodoItemEntity::getDueAt)
                .last("LIMIT " + limit)
                .list()
                .stream()
                .map(entity -> todoService.getTodoDetail(entity.getId(), userId))
                .toList();
    }

    private String formatOverdueTodosForPrompt(List<TodoVO> todos) {
        if (todos.isEmpty()) {
            return "（无）";
        }
        StringBuilder builder = new StringBuilder();
        for (TodoVO todo : todos) {
            builder.append("- id=").append(todo.getId())
                    .append(" | ").append(todo.getTitle())
                    .append(" | 截止 ").append(todo.getDueAt())
                    .append(" | 状态 ").append(todo.getStatus() == 1 ? "进行中" : "待开始");
            if (StringUtils.hasText(todo.getDescription())) {
                builder.append(" | ").append(todo.getDescription().trim());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private TodoOverdueAdviceVO parseOverdueAdvice(String content, Set<Long> allowedIds) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            String summary = readText(node, "summary", "建议优先处理逾期项，能拆则拆、能改期则改期。");
            JsonNode suggestions = node.get("suggestions");
            List<TodoOverdueAdviceItemVO> items = new ArrayList<>();
            if (suggestions != null && suggestions.isArray()) {
                suggestions.forEach(item -> {
                    if (item == null || !item.isObject()) {
                        return;
                    }
                    Long todoId = item.has("todoId") ? item.get("todoId").asLong(0) : 0L;
                    if (todoId <= 0 || !allowedIds.contains(todoId)) {
                        return;
                    }
                    String title = readText(item, "title", null);
                    if (!StringUtils.hasText(title)) {
                        return;
                    }
                    String action = readText(item, "action", "reschedule");
                    items.add(new TodoOverdueAdviceItemVO(
                            todoId,
                            title,
                            action,
                            readText(item, "reason", "建议处理"),
                            parseExtractedDueAt(item.get("suggestedDueAt"))
                    ));
                });
            }
            return new TodoOverdueAdviceVO(summary, items.stream().limit(8).toList());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "逾期待办建议解析失败：" + ex.getMessage());
        }
    }

    private List<NoteEntity> collectSynthesisSources(
            NoteSynthesizeRequest request,
            Long userId,
            Long workspaceId,
            String topic
    ) {
        Map<Long, NoteEntity> ranked = new LinkedHashMap<>();

        if (request.getNoteIds() != null && !request.getNoteIds().isEmpty()) {
            for (Long noteId : request.getNoteIds().stream().distinct().limit(MAX_SYNTHESIS_NOTES).toList()) {
                if (noteId == null) {
                    continue;
                }
                NoteEntity note = noteService.requireOwnedNote(noteId, userId);
                if (!workspaceId.equals(note.getWorkspaceId())) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文档不属于当前知识库");
                }
                ranked.put(note.getId(), note);
            }
            return ranked.values().stream().limit(MAX_SYNTHESIS_NOTES).toList();
        }

        List<RetrievedNoteChunk> retrieved = noteRetrievalService.retrieveForQuestion(userId, workspaceId, topic);
        for (RetrievedNoteChunk chunk : retrieved) {
            if (chunk.noteId() == null || ranked.size() >= MAX_SYNTHESIS_NOTES) {
                continue;
            }
            try {
                NoteEntity note = noteService.requireOwnedNote(chunk.noteId(), userId);
                if (matchesDateRange(note, request.getDateFrom(), request.getDateTo())) {
                    ranked.putIfAbsent(note.getId(), note);
                }
            } catch (BizException ignored) {
                // skip inaccessible notes
            }
        }

        LambdaQueryWrapper<NoteEntity> wrapper = new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(NoteEntity::getWorkspaceId, workspaceId)
                .and(query -> query
                        .like(NoteEntity::getTitle, topic)
                        .or()
                        .like(NoteEntity::getContent, topic)
                        .or()
                        .like(NoteEntity::getSummary, topic))
                .orderByDesc(NoteEntity::getLastEditedAt)
                .last("LIMIT " + MAX_SYNTHESIS_NOTES);
        applyDateRange(wrapper, request.getDateFrom(), request.getDateTo());
        for (NoteEntity note : noteService.list(wrapper)) {
            ranked.putIfAbsent(note.getId(), note);
            if (ranked.size() >= MAX_SYNTHESIS_NOTES) {
                break;
            }
        }

        if (ranked.size() < MAX_SYNTHESIS_NOTES) {
            LambdaQueryWrapper<NoteEntity> recentWrapper = new LambdaQueryWrapper<NoteEntity>()
                    .eq(NoteEntity::getCreatedBy, userId)
                    .eq(NoteEntity::getWorkspaceId, workspaceId)
                    .orderByDesc(NoteEntity::getLastEditedAt)
                    .last("LIMIT " + MAX_SYNTHESIS_NOTES);
            applyDateRange(recentWrapper, request.getDateFrom(), request.getDateTo());
            for (NoteEntity note : noteService.list(recentWrapper)) {
                ranked.putIfAbsent(note.getId(), note);
                if (ranked.size() >= MAX_SYNTHESIS_NOTES) {
                    break;
                }
            }
        }

        return ranked.values().stream()
                .sorted(Comparator.comparing(
                        (NoteEntity n) -> n.getLastEditedAt() == null ? LocalDateTime.MIN : n.getLastEditedAt()
                ).reversed())
                .limit(MAX_SYNTHESIS_NOTES)
                .toList();
    }

    private void applyDateRange(
            LambdaQueryWrapper<NoteEntity> wrapper,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null) {
            wrapper.ge(NoteEntity::getLastEditedAt, dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            wrapper.lt(NoteEntity::getLastEditedAt, dateTo.plusDays(1).atStartOfDay());
        }
    }

    private boolean matchesDateRange(NoteEntity note, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime editedAt = note.getLastEditedAt();
        if (editedAt == null) {
            return dateFrom == null && dateTo == null;
        }
        if (dateFrom != null && editedAt.isBefore(dateFrom.atStartOfDay())) {
            return false;
        }
        if (dateTo != null && !editedAt.isBefore(dateTo.plusDays(1).atStartOfDay())) {
            return false;
        }
        return true;
    }

    private String buildSynthesisMaterial(List<NoteEntity> notes) {
        StringBuilder builder = new StringBuilder();
        for (NoteEntity note : notes) {
            builder.append("### [文档 #").append(note.getId()).append("] ")
                    .append(note.getTitle() == null ? "未命名" : note.getTitle()).append('\n');
            if (note.getLastEditedAt() != null) {
                builder.append("_编辑于 ").append(note.getLastEditedAt().toLocalDate()).append("_\n\n");
            }
            builder.append(truncateForSynthesis(note.getContent())).append("\n\n---\n\n");
        }
        return builder.toString().trim();
    }

    private String truncateForSynthesis(String content) {
        if (!StringUtils.hasText(content)) {
            return "（空文档）";
        }
        if (content.length() <= MAX_SYNTHESIS_CHARS_PER_NOTE) {
            return content;
        }
        return content.substring(0, MAX_SYNTHESIS_CHARS_PER_NOTE) + "\n...(已截断)";
    }

    private String buildSynthesisPrompt(
            String template,
            String topic,
            String material,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String rangeHint = "";
        if (dateFrom != null || dateTo != null) {
            rangeHint = "时间范围：" + (dateFrom != null ? dateFrom : "不限") + " 至 " + (dateTo != null ? dateTo : "不限") + "\n";
        }
        String base = """
                主题：%s
                %s
                以下是多篇相关文档素材（可能已截断），请综合提炼，不要编造素材中不存在的事实；不足处用「待补充」占位。
                素材：
                %s
                """.formatted(topic, rangeHint, material);

        return switch (template) {
            case "decision" -> """
                    你是知识管理助手。根据以下多篇文档素材，生成一份「决策记录」Markdown，包含：
                    ## 背景
                    ## 关键决策
                    ## 依据与取舍
                    ## 后续行动
                    ## 风险与待确认
                    要求：合并重复信息；行动项可用 - [ ] 格式；仅输出 Markdown 正文。

                    """ + base;
            case "status" -> """
                    你是项目助理。根据以下多篇文档素材，生成「项目现状」Markdown，包含：
                    ## 概览
                    ## 当前进展
                    ## 问题与阻塞
                    ## 下一步
                    ## 待确认
                    要求：突出最新状态；仅输出 Markdown 正文。

                    """ + base;
            case "weekly" -> """
                    你是周报写作助手。根据以下多篇文档素材，生成「周报草稿」Markdown，包含：
                    ## 本周完成
                    ## 进行中
                    ## 下周计划
                    ## 风险与阻塞
                    要求：按时间线归纳；仅输出 Markdown 正文。

                    """ + base;
            case "retro" -> """
                    你是复盘助手。根据以下多篇文档素材，生成「复盘草稿」Markdown，包含：
                    ## 目标回顾
                    ## 做得好的
                    ## 待改进
                    ## 行动项
                    要求：行动项可用 - [ ] 格式；仅输出 Markdown 正文。

                    """ + base;
            default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的合成模板：" + template);
        };
    }

    private String buildSynthesisTitle(String template, String topic) {
        String label = switch (template) {
            case "decision" -> "决策记录";
            case "status" -> "项目现状";
            case "weekly" -> "周报";
            case "retro" -> "复盘";
            default -> "合成笔记";
        };
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String trimmedTopic = topic.length() > 40 ? topic.substring(0, 40) + "…" : topic;
        return "【" + label + "】" + trimmedTopic + " · " + date;
    }

    private String appendSourceSection(String content, List<NoteEntity> sources) {
        if (sources.isEmpty()) {
            return content;
        }
        StringBuilder builder = new StringBuilder(content.trim());
        builder.append("\n\n---\n\n## 参考文档\n");
        for (NoteEntity note : sources) {
            builder.append("- ").append(note.getTitle() == null ? "未命名" : note.getTitle())
                    .append(" (#").append(note.getId()).append(")\n");
        }
        return builder.toString();
    }

    private AiAskContext buildAskContext(AiAskRequest request, Long userId) {
        String scope = request.getScope() == null ? "workspace" : request.getScope().trim();
        List<AiReferenceVO> references = new ArrayList<>();
        String context;
        String question = request.getQuestion().trim();
        String sessionBlock = request.getSessionId() == null
                ? ""
                : aiChatSessionService.buildRecentDialogBlock(request.getSessionId(), userId, 8);
        String dialogBlock = AiConversationContext.resolveContextBlock(request.getRecentContext(), sessionBlock);
        String retrievalQuery = AiConversationContext.expandRetrievalQuery(question, dialogBlock);
        List<RetrievedNoteChunk> chunks;

        if ("note".equalsIgnoreCase(scope)) {
            if (request.getTargetId() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            NoteEntity note = noteService.requireOwnedNote(request.getTargetId(), userId);
            chunks = noteRetrievalService.retrieveForNote(userId, note, retrievalQuery);
            for (RetrievedNoteChunk chunk : chunks) {
                references.add(noteRetrievalService.toReference(chunk));
            }
            context = noteRetrievalService.buildSingleNoteContext(note, retrievalQuery, chunks);
        } else {
            if (request.getWorkspaceId() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            chunks = noteRetrievalService.retrieveForQuestion(
                    userId, request.getWorkspaceId(), retrievalQuery
            );
            for (RetrievedNoteChunk chunk : chunks) {
                references.add(noteRetrievalService.toReference(chunk));
            }
            context = noteRetrievalService.buildChunkContext(chunks);
        }

        boolean catalogQuestion = !"note".equalsIgnoreCase(scope)
                && noteRetrievalService.isCatalogQuestion(question);
        String catalogSection = "";
        String catalogInstruction = "";
        if (catalogQuestion && request.getWorkspaceId() != null) {
            catalogSection = noteRetrievalService.buildCatalogContext(
                    userId, request.getWorkspaceId(), question
            );
            catalogInstruction = """
                    用户正在询问文档列表/目录。请优先依据下方「知识库目录」直接列出文档标题（可按分组整理），不要回答「资料中未找到」。
                    """;
        }

        List<String> retrievalGaps = catalogQuestion
                ? List.of()
                : noteRetrievalService.inferRetrievalGaps(chunks, retrievalQuery);
        String styleInstruction = userSettingService.resolveAnswerStyleInstruction(userId);
        String memoryBlock = userSettingService.resolveLightMemoryPromptBlock(userId);
        String gapInstruction = retrievalGaps.isEmpty()
                ? ""
                : "当前检索匹配偏弱，请在回答末尾 KNOWLEDGE_GAPS 行给出 1-2 条可新建的笔记主题建议。";
        String memorySection = StringUtils.hasText(memoryBlock) ? memoryBlock + "\n\n" : "";
        String catalogBlock = StringUtils.hasText(catalogSection)
                ? """
                知识库目录：
                %s

                """.formatted(catalogSection)
                : "";
        String dialogSection = StringUtils.hasText(dialogBlock)
                ? """
                对话历史（用于理解指代，如「这个」「上面」「第二点」；当前问题是最新一轮）：
                %s

                """.formatted(dialogBlock)
                : "";
        String prompt = """
                你是 Noto 知微的知识助手。基于给定资料，用中文直接、清晰地回答用户当前问题。
                资料来自知识库分块检索（可能来自同一文档的不同段落），请优先依据资料作答。
                若对话历史中有上下文，请结合历史理解省略主语、指代和追问（如「详细说说」「什么意思」）。
                如果资料不足以回答，请明确说明「资料中未找到足够信息」，不要编造。
                若资料不足，请在回答最后一行追加（仅此一行）：
                KNOWLEDGE_GAPS: 建议补充的笔记主题1；建议2
                资料充足时不要输出 KNOWLEDGE_GAPS 行。
                %s
                %s
                %s
                %s
                %s
                资料（正文片段）：
                %s

                当前问题：%s
                """.formatted(
                styleInstruction,
                memorySection,
                catalogInstruction,
                catalogBlock,
                dialogSection,
                gapInstruction,
                context,
                question
        );

        return new AiAskContext(prompt, references, retrievalGaps);
    }

    private List<String> mergeKnowledgeGaps(List<String> modelGaps, List<String> retrievalGaps) {
        List<String> merged = new ArrayList<>();
        if (retrievalGaps != null) {
            merged.addAll(retrievalGaps);
        }
        if (modelGaps != null) {
            for (String gap : modelGaps) {
                if (StringUtils.hasText(gap) && !merged.contains(gap.trim())) {
                    merged.add(gap.trim());
                }
            }
        }
        return merged.stream().limit(4).toList();
    }

    private String buildTransformPrompt(String mode, String title, String content) {
        return switch (mode) {
            case "polish" -> """
                    你是中文写作助手。请润色以下 Markdown 文档，改进表达与可读性，保持原有结构与信息，不要删减关键事实。
                    要求：仅输出润色后的 Markdown 正文，不要代码块包裹，不要额外解释。

                    标题：%s
                    正文：
                    %s
                    """.formatted(title, truncate(content));
            case "bulletize" -> """
                    你是笔记整理助手。请将以下 Markdown 整理为清晰的条目化结构（适当使用标题、列表、要点），保留全部关键信息。
                    要求：仅输出整理后的 Markdown 正文，不要代码块包裹，不要额外解释。

                    标题：%s
                    正文：
                    %s
                    """.formatted(title, truncate(content));
            case "structure" -> """
                    你是会议纪要整理助手。请将以下内容整理为结构化 Markdown，包含以下章节（无内容可写「无」）：
                    ## 结论
                    ## 决策
                    ## 待办
                    ## 风险
                    要求：保留关键信息；待办用 - [ ] 格式；仅输出 Markdown 正文。

                    标题：%s
                    正文：
                    %s
                    """.formatted(title, truncate(content));
            case "template_weekly" -> """
                    你是周报写作助手。根据以下素材生成「周报」Markdown 草稿，包含：本周完成、进行中、下周计划、风险与阻塞。
                    素材不足处用「待补充」占位。仅输出 Markdown 正文。

                    标题：%s
                    素材：
                    %s
                    """.formatted(title, truncate(content));
            case "template_retro" -> """
                    你是项目复盘助手。根据以下素材生成「项目复盘」Markdown 草稿，包含：目标回顾、做得好的、待改进、行动项。
                    素材不足处用「待补充」占位。仅输出 Markdown 正文。

                    标题：%s
                    素材：
                    %s
                    """.formatted(title, truncate(content));
            case "template_proposal" -> """
                    你是方案写作助手。根据以下素材生成「方案初稿」Markdown，包含：背景、目标、方案要点、里程碑、风险。
                    素材不足处用「待补充」占位。仅输出 Markdown 正文。

                    标题：%s
                    素材：
                    %s
                    """.formatted(title, truncate(content));
            default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的 transform 模式：" + mode);
        };
    }

    private String buildSelectionTransformPrompt(String mode, String title, String selected) {
        String truncated = selected.length() > 3000 ? selected.substring(0, 3000) + "\n...(已截断)" : selected;
        return switch (mode) {
            case "concise" -> """
                    你是写作助手。将以下选中段落改写得更简洁，保留关键信息。仅输出改写后的 Markdown 片段，不要解释。

                    文档标题：%s
                    选中段落：
                    %s
                    """.formatted(title, truncated);
            case "expand" -> """
                    你是写作助手。将以下选中段落适当扩写，补充细节与逻辑，保持 Markdown 格式。仅输出改写后的片段。

                    文档标题：%s
                    选中段落：
                    %s
                    """.formatted(title, truncated);
            case "formal" -> """
                    你是写作助手。将以下选中段落改为更正式、专业的语气，保持 Markdown 格式。仅输出改写后的片段。

                    文档标题：%s
                    选中段落：
                    %s
                    """.formatted(title, truncated);
            default -> throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "不支持的选中改写模式：" + mode);
        };
    }

    private record ParsedAnswer(String answer, List<String> knowledgeGaps) {}

    private ParsedAnswer parseAnswerWithGaps(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ParsedAnswer("", List.of());
        }
        int idx = raw.lastIndexOf("KNOWLEDGE_GAPS:");
        if (idx < 0) {
            return new ParsedAnswer(raw.trim(), List.of());
        }
        String answer = raw.substring(0, idx).trim();
        String gapsPart = raw.substring(idx + "KNOWLEDGE_GAPS:".length()).trim();
        List<String> gaps = new ArrayList<>();
        if (StringUtils.hasText(gapsPart)) {
            for (String item : gapsPart.split("[；;\\n]")) {
                String trimmed = item.trim();
                if (StringUtils.hasText(trimmed)) {
                    gaps.add(trimmed);
                }
            }
        }
        return new ParsedAnswer(answer, gaps);
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "未配置 AI_DASHSCOPE_API_KEY");
        }
    }

    private String callModel(
            String prompt,
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId
    ) {
        long startedAt = System.currentTimeMillis();
        try {
            String content = chatModel.chat(prompt);
            if (!StringUtils.hasText(content)) {
                throw new BizException(ErrorCode.AI_ERROR.getCode(), "模型未返回内容");
            }
            String trimmed = content.trim();
            recordAudit(userId, workspaceId, actionType, resourceType, resourceId, prompt, trimmed, true, null, System.currentTimeMillis() - startedAt);
            return trimmed;
        } catch (BizException ex) {
            recordAudit(userId, workspaceId, actionType, resourceType, resourceId, prompt, null, false, ex.getMessage(), System.currentTimeMillis() - startedAt);
            throw ex;
        } catch (Exception ex) {
            recordAudit(userId, workspaceId, actionType, resourceType, resourceId, prompt, null, false, ex.getMessage(), System.currentTimeMillis() - startedAt);
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "LangChain4j 调用失败：" + ex.getMessage());
        }
    }

    private void recordAudit(
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId,
            String prompt,
            String response,
            boolean success,
            String errorMessage,
            long durationMs
    ) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("model", aiProperties.getModel());
        detail.put("provider", "langchain4j-dashscope");
        detail.put("success", success);
        detail.put("durationMs", durationMs);
        detail.put("promptPreview", preview(prompt));
        if (StringUtils.hasText(response)) {
            detail.put("responseLength", response.length());
            detail.put("responsePreview", preview(response));
        }
        if (StringUtils.hasText(errorMessage)) {
            detail.put("error", errorMessage);
        }
        auditLogService.logAiCall(userId, workspaceId, actionType, resourceType, resourceId, detail);
    }

    private String preview(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= PROMPT_PREVIEW_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PROMPT_PREVIEW_CHARS) + "...";
    }

    private List<ExtractedTodoItem> parseExtractedTodos(String content) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            JsonNode todos = node.get("todos");
            if (todos == null || !todos.isArray()) {
                return List.of();
            }
            List<ExtractedTodoItem> items = new ArrayList<>();
            todos.forEach(item -> {
                if (item == null || !item.isObject()) {
                    return;
                }
                String title = readText(item, "title", null);
                if (!StringUtils.hasText(title)) {
                    return;
                }
                boolean completed = item.has("completed") && item.get("completed").asBoolean(false);
                Integer priority = item.has("priority") ? item.get("priority").asInt(2) : 2;
                String horizon = readText(item, "horizon", null);
                LocalDateTime dueAt = parseExtractedDueAt(item.get("dueAt"));
                items.add(new ExtractedTodoItem(title.trim(), completed, priority, horizon, dueAt));
            });
            return items;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 待办解析失败：" + ex.getMessage());
        }
    }

    private LocalDateTime parseExtractedDueAt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return ExtractedTodoDueHelper.parseDueAt(node.asText());
        }
        return null;
    }

    private NoteSummaryVO parseSummary(String content, String fallbackTitle) {
        NoteSummaryVO vo = new NoteSummaryVO();
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            vo.setSummary(readText(node, "summary", fallbackTitle));
            vo.setKeyPoints(readStringList(node, "keyPoints"));
            vo.setRiskPoints(readStringList(node, "riskPoints"));
            return vo;
        } catch (Exception ignored) {
            vo.setSummary(content.length() > 200 ? content.substring(0, 200) : content);
            vo.setKeyPoints(List.of());
            vo.setRiskPoints(List.of());
            return vo;
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String readText(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
            return value.asText().trim();
        }
        return fallback;
    }

    private List<String> readStringList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        value.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                items.add(item.asText().trim());
            }
        });
        return items;
    }

    private AiDailySuggestionsVO parseDailySuggestions(String content, Set<Long> allowedIds) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            String summary = readText(node, "summary", "今日按优先级推进关键待办。");
            JsonNode suggestions = node.get("suggestions");
            List<AiDailySuggestionVO> items = new ArrayList<>();
            if (suggestions != null && suggestions.isArray()) {
                suggestions.forEach(item -> {
                    if (item == null || !item.isObject()) {
                        return;
                    }
                    Long todoId = item.has("todoId") ? item.get("todoId").asLong(0) : 0L;
                    if (todoId <= 0 || !allowedIds.contains(todoId)) {
                        return;
                    }
                    String title = readText(item, "title", null);
                    if (!StringUtils.hasText(title)) {
                        return;
                    }
                    items.add(new AiDailySuggestionVO(
                            todoId,
                            title,
                            readText(item, "reason", "建议优先处理"),
                            readText(item, "action", "focus")
                    ));
                });
            }
            return new AiDailySuggestionsVO(summary, items.stream().limit(3).toList());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 今日建议解析失败：" + ex.getMessage());
        }
    }

    private List<AiSubtaskSuggestionVO> parseSubtasks(String content) {
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            JsonNode subtasks = node.get("subtasks");
            if (subtasks == null || !subtasks.isArray()) {
                return List.of();
            }
            List<AiSubtaskSuggestionVO> items = new ArrayList<>();
            subtasks.forEach(item -> {
                if (item == null || !item.isObject()) {
                    return;
                }
                String title = readText(item, "title", null);
                if (!StringUtils.hasText(title)) {
                    return;
                }
                Integer priority = item.has("priority") ? item.get("priority").asInt(2) : 2;
                String horizon = readText(item, "horizon", "action");
                LocalDateTime dueAt = parseExtractedDueAt(item.get("dueAt"));
                items.add(new AiSubtaskSuggestionVO(title.trim(), priority, horizon, dueAt));
            });
            return items;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 任务拆解解析失败：" + ex.getMessage());
        }
    }

    private AiDailyReviewVO parseDailyReview(String content) {
        AiDailyReviewVO review = new AiDailyReviewVO();
        review.setCompletedCount(0);
        try {
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);
            review.setSummary(readText(node, "summary", "今日工作已记录，继续保持节奏。"));
            review.setHighlights(readStringList(node, "highlights"));
            review.setBlockers(readStringList(node, "blockers"));
            review.setTomorrowFocus(readStringList(node, "tomorrowFocus"));
            return review;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 日复盘解析失败：" + ex.getMessage());
        }
    }

    private String formatCompletedForReview(List<TodoVO> todos) {
        if (todos == null || todos.isEmpty()) {
            return "（今日暂无完成项）";
        }
        return todos.stream()
                .map(item -> "- %s | completedAt=%s".formatted(
                        item.getTitle(),
                        item.getCompletedAt() == null ? "未知" : item.getCompletedAt().toString()
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatTodosForPrompt(List<TodoVO> todos) {
        if (todos == null || todos.isEmpty()) {
            return "（无）";
        }
        return todos.stream()
                .map(item -> "- id=%d | %s | priority=%d | due=%s".formatted(
                        item.getId(),
                        item.getTitle(),
                        item.getPriority(),
                        item.getDueAt() == null ? "无" : item.getDueAt().toString()
                ))
                .collect(Collectors.joining("\n"));
    }

    private String resolveDraftTitle(NoteAiDraftRequest draft, String fallback) {
        if (draft != null && StringUtils.hasText(draft.getTitle())) {
            return draft.getTitle().trim();
        }
        return fallback == null ? "无标题" : fallback;
    }

    private String resolveDraftContent(NoteAiDraftRequest draft, String fallback) {
        if (draft != null && draft.getContent() != null) {
            return draft.getContent();
        }
        return fallback;
    }

    private String stripMarkdownFence(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstBreak > 0 && lastFence > firstBreak) {
                return trimmed.substring(firstBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String truncate(String content) {
        return materialForLongDocument(content, null);
    }

    private String materialForLongDocument(String content, String question) {
        if (content == null || content.isBlank()) {
            return "（空文档）";
        }
        if (content.length() <= MAX_NOTE_CHARS) {
            return content;
        }
        List<NoteChunkSplitter.ContentChunk> selected = NoteContentChunkSelector.selectForQuery(
                content,
                question,
                12,
                aiProperties.getRagChunkSize(),
                aiProperties.getRagChunkOverlap()
        );
        String joined = NoteContentChunkSelector.joinForPrompt(selected, MAX_CHUNKED_MATERIAL_CHARS);
        if (!StringUtils.hasText(joined)) {
            return content.substring(0, MAX_NOTE_CHARS) + "\n...(内容已截断)";
        }
        return joined + "\n\n（长文已按段落分块摘录，非简单截断）";
    }
}
