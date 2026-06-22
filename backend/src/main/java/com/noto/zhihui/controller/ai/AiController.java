package com.noto.zhihui.controller.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.dto.ai.AiAskRequest;
import com.noto.zhihui.dto.ai.AiRouteRequest;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.ai.NoteAiDraftRequest;
import com.noto.zhihui.dto.ai.NoteSelectionTransformRequest;
import com.noto.zhihui.dto.ai.NoteSynthesizeRequest;
import com.noto.zhihui.dto.ai.NoteTransformRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.agent.AiIntentRouter;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.service.AiDigestService;
import com.noto.zhihui.service.AiWeeklyRetroService;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.AiStreamSink;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.vo.ai.AiAskVO;
import com.noto.zhihui.vo.ai.AiReferenceVO;
import com.noto.zhihui.vo.ai.AiRouteVO;
import com.noto.zhihui.vo.ai.AiStatusVO;
import com.noto.zhihui.vo.ai.AiObservabilitySummaryVO;
import com.noto.zhihui.vo.ai.NoteSummaryVO;
import com.noto.zhihui.vo.ai.NoteSynthesizeVO;
import com.noto.zhihui.vo.ai.NoteTransformVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.dto.ai.TodoBreakdownRequest;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionsVO;
import com.noto.zhihui.vo.ai.AiDigestVO;
import com.noto.zhihui.vo.ai.AiWeeklyRetroVO;
import com.noto.zhihui.dto.ai.TodoCompletionRetroRequest;
import com.noto.zhihui.vo.ai.TodoCompletionRetroVO;
import com.noto.zhihui.vo.ai.TodoBreakdownVO;
import com.noto.zhihui.vo.ai.TodoOverdueAdviceVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;
    private final AiIntentRouter aiIntentRouter;
    private final AiDigestService aiDigestService;
    private final AiWeeklyRetroService aiWeeklyRetroService;
    private final AiChatSessionService aiChatSessionService;
    private final AuditLogService auditLogService;
    private final NoteService noteService;
    private final NotoAiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiController(
            AiService aiService,
            AiIntentRouter aiIntentRouter,
            AiDigestService aiDigestService,
            AiWeeklyRetroService aiWeeklyRetroService,
            AiChatSessionService aiChatSessionService,
            AuditLogService auditLogService,
            NoteService noteService,
            NotoAiProperties aiProperties,
            ObjectMapper objectMapper
    ) {
        this.aiService = aiService;
        this.aiIntentRouter = aiIntentRouter;
        this.aiDigestService = aiDigestService;
        this.aiWeeklyRetroService = aiWeeklyRetroService;
        this.aiChatSessionService = aiChatSessionService;
        this.auditLogService = auditLogService;
        this.noteService = noteService;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public ApiResponse<AiStatusVO> status() {
        return ApiResponse.success(aiService.status(), null);
    }

    @GetMapping("/observability/summary")
    public ApiResponse<AiObservabilitySummaryVO> observabilitySummary() {
        return ApiResponse.success(auditLogService.aiObservabilitySummary(requireUserId()), null);
    }

    @PostMapping("/notes/{id}/summarize")
    public ApiResponse<NoteSummaryVO> summarize(
            @PathVariable Long id,
            @RequestBody(required = false) NoteAiDraftRequest draft,
            @RequestParam(defaultValue = "true") boolean persist
    ) {
        Long userId = requireUserId();
        NoteSummaryVO summary = aiService.summarizeNote(id, userId, draft);
        if (persist) {
            noteService.updateSummary(id, userId, summary.getSummary());
        }
        return ApiResponse.success(summary, null);
    }

    @PostMapping("/notes/{id}/transform")
    public ApiResponse<NoteTransformVO> transform(
            @PathVariable Long id,
            @Valid @RequestBody NoteTransformRequest request
    ) {
        return ApiResponse.success(aiService.transformNote(id, requireUserId(), request), null);
    }

    @PostMapping(value = "/notes/{id}/transform-selection/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter transformSelectionStream(
            @PathVariable Long id,
            @Valid @RequestBody NoteSelectionTransformRequest request
    ) {
        Long userId = requireUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        aiService.transformSelectionStream(id, request, userId, new AiStreamSink() {
            @Override
            public void sendReferences(List<AiReferenceVO> references) {
            }

            @Override
            public void sendToken(String token) {
                sendEvent(emitter, "token", token);
            }

            @Override
            public void complete() {
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            }

            @Override
            public void fail(String message) {
                sendEvent(emitter, "error", message);
                emitter.complete();
            }
        });
        return emitter;
    }

    @PostMapping("/notes/{id}/extract-todos")
    public ApiResponse<NoteExtractTodosPreviewVO> extractTodos(@PathVariable Long id) {
        return ApiResponse.success(aiService.previewExtractTodosFromNote(id, requireUserId()), null);
    }

    @PostMapping("/notes/{id}/extract-todos/confirm")
    public ApiResponse<NoteExtractTodosVO> confirmExtractTodos(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmExtractTodosRequest request
    ) {
        return ApiResponse.success(aiService.confirmExtractTodosFromNote(id, requireUserId(), request), null);
    }

    @GetMapping("/digest/today")
    public ApiResponse<AiDigestVO> todayDigest() {
        return ApiResponse.success(aiDigestService.getTodayDigest(requireUserId()), null);
    }

    @PostMapping("/digest/generate")
    public ApiResponse<AiDigestVO> generateDigest(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(aiDigestService.generateDigest(requireUserId(), workspaceId), null);
    }

    @GetMapping("/weekly-retro/current")
    public ApiResponse<AiWeeklyRetroVO> currentWeeklyRetro() {
        return ApiResponse.success(aiWeeklyRetroService.getThisWeekRetro(requireUserId()), null);
    }

    @PostMapping("/weekly-retro/generate")
    public ApiResponse<AiWeeklyRetroVO> generateWeeklyRetro(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(aiWeeklyRetroService.generateWeeklyRetro(requireUserId(), workspaceId), null);
    }

    @PostMapping("/route")
    public ApiResponse<AiRouteVO> route(@Valid @RequestBody AiRouteRequest request) {
        Long userId = requireUserId();
        return ApiResponse.success(
                aiIntentRouter.route(
                        request.getMessage(),
                        request.getSessionId(),
                        userId,
                        request.getRecentContext()
                ),
                null
        );
    }

    @PostMapping("/ask")
    public ApiResponse<AiAskVO> ask(@Valid @RequestBody AiAskRequest request) {
        return ApiResponse.success(aiService.ask(request, requireUserId()), null);
    }

    @GetMapping("/todos/daily-suggestions")
    public ApiResponse<AiDailySuggestionsVO> dailySuggestions(
            @RequestParam(required = false) Long workspaceId
    ) {
        return ApiResponse.success(aiService.suggestDailyActions(requireUserId(), workspaceId), null);
    }

    @GetMapping("/todos/daily-review")
    public ApiResponse<AiDailyReviewVO> dailyReview(
            @RequestParam(required = false) Long workspaceId
    ) {
        return ApiResponse.success(aiService.reviewDailyProgress(requireUserId(), workspaceId), null);
    }

    @PostMapping("/todos/breakdown")
    public ApiResponse<TodoBreakdownVO> breakdown(@Valid @RequestBody TodoBreakdownRequest request) {
        return ApiResponse.success(aiService.breakdownTodo(request, requireUserId()), null);
    }

    @PostMapping("/notes/synthesize")
    public ApiResponse<NoteSynthesizeVO> synthesizeNotes(@Valid @RequestBody NoteSynthesizeRequest request) {
        return ApiResponse.success(aiService.synthesizeNotes(request, requireUserId()), null);
    }

    @GetMapping("/todos/overdue-advice")
    public ApiResponse<TodoOverdueAdviceVO> overdueAdvice(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(aiService.suggestOverdueTodoActions(requireUserId(), workspaceId), null);
    }

    @PostMapping("/todos/{todoId}/completion-retro")
    public ApiResponse<TodoCompletionRetroVO> completionRetro(
            @PathVariable Long todoId,
            @RequestBody(required = false) TodoCompletionRetroRequest request
    ) {
        TodoCompletionRetroRequest body = request == null ? new TodoCompletionRetroRequest() : request;
        return ApiResponse.success(aiService.completionRetro(todoId, body, requireUserId()), null);
    }

    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @ModelAttribute AiAskRequest request) {
        Long userId = requireUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> UserContext.clear());
        emitter.onTimeout(() -> UserContext.clear());
        emitter.onError(ex -> UserContext.clear());

        StringBuilder answerBuilder = new StringBuilder();
        List<AiReferenceVO> references = new ArrayList<>();

        aiService.askStream(request, userId, new AiStreamSink() {
            @Override
            public void sendReferences(List<AiReferenceVO> refs) {
                references.clear();
                if (refs != null) {
                    references.addAll(refs);
                }
                sendEvent(emitter, "references", refs);
            }

            @Override
            public void sendToken(String token) {
                answerBuilder.append(token);
                sendEvent(emitter, "token", token);
            }

            @Override
            public void sendKnowledgeGaps(java.util.List<String> gaps) {
                sendEvent(emitter, "knowledge_gaps", gaps);
            }

            @Override
            public void complete() {
                persistSessionExchange(request, userId, answerBuilder.toString(), references);
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            }

            @Override
            public void fail(String message) {
                sendEvent(emitter, "error", message);
                emitter.complete();
            }
        });
        return emitter;
    }

    private void persistSessionExchange(
            AiAskRequest request,
            Long userId,
            String answer,
            List<AiReferenceVO> references
    ) {
        if (request.getSessionId() == null) {
            return;
        }
        try {
            aiChatSessionService.saveExchange(
                    request.getSessionId(),
                    userId,
                    request.getQuestion(),
                    answer,
                    references,
                    aiProperties.getModel()
            );
        } catch (Exception ignored) {
            // 持久化失败不影响流式响应
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            Object payload = data instanceof String ? data : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
