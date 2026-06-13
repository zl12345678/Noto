package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.dto.ai.AiAskRequest;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.ai.NoteAiDraftRequest;
import com.noto.zhihui.dto.ai.NoteSelectionTransformRequest;
import com.noto.zhihui.dto.ai.NoteSynthesizeRequest;
import com.noto.zhihui.dto.ai.NoteTransformRequest;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.AiStreamSink;
import com.noto.zhihui.vo.ai.AiAskVO;
import com.noto.zhihui.vo.ai.AiStatusVO;
import com.noto.zhihui.vo.ai.NoteSummaryVO;
import com.noto.zhihui.vo.ai.NoteSynthesizeVO;
import com.noto.zhihui.vo.ai.NoteTransformVO;
import com.noto.zhihui.dto.ai.TodoBreakdownRequest;
import com.noto.zhihui.dto.ai.TodoCompletionRetroRequest;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionsVO;
import com.noto.zhihui.vo.ai.TodoOverdueAdviceVO;
import com.noto.zhihui.vo.ai.TodoBreakdownVO;
import com.noto.zhihui.vo.ai.TodoCompletionRetroVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiServiceImpl implements AiService {

    private final NotoAiProperties aiProperties;

    public DisabledAiServiceImpl(NotoAiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public AiStatusVO status() {
        return new AiStatusVO(
                false,
                StringUtils.hasText(aiProperties.getApiKey()),
                aiProperties.getModel(),
                "langchain4j-dashscope"
        );
    }

    @Override
    public NoteSummaryVO summarizeNote(Long noteId, Long userId, NoteAiDraftRequest draft) {
        throw disabled();
    }

    @Override
    public NoteTransformVO transformNote(Long noteId, Long userId, NoteTransformRequest request) {
        throw disabled();
    }

    @Override
    public void transformSelectionStream(
            Long noteId,
            NoteSelectionTransformRequest request,
            Long userId,
            AiStreamSink sink
    ) {
        sink.fail("AI 未启用。请设置 NOTO_AI_ENABLED=true 和 AI_DASHSCOPE_API_KEY 后重启后端");
    }

    @Override
    public AiAskVO ask(AiAskRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public void askStream(AiAskRequest request, Long userId, AiStreamSink sink) {
        sink.fail("AI 未启用。请设置 NOTO_AI_ENABLED=true 和 AI_DASHSCOPE_API_KEY 后重启后端");
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractTodosFromNote(Long noteId, Long userId) {
        throw disabled();
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractTodosFromContent(
            String title,
            String content,
            Long workspaceId,
            Long userId
    ) {
        throw disabled();
    }

    @Override
    public NoteExtractTodosVO confirmExtractTodosFromNote(Long noteId, Long userId, ConfirmExtractTodosRequest request) {
        throw disabled();
    }

    @Override
    public AiDailySuggestionsVO suggestDailyActions(Long userId, Long workspaceId) {
        throw disabled();
    }

    @Override
    public AiDailyReviewVO reviewDailyProgress(Long userId, Long workspaceId) {
        throw disabled();
    }

    @Override
    public TodoBreakdownVO breakdownTodo(TodoBreakdownRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public NoteSynthesizeVO synthesizeNotes(NoteSynthesizeRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public TodoOverdueAdviceVO suggestOverdueTodoActions(Long userId, Long workspaceId) {
        throw disabled();
    }

    @Override
    public TodoCompletionRetroVO completionRetro(Long todoId, TodoCompletionRetroRequest request, Long userId) {
        throw disabled();
    }

    private BizException disabled() {
        return new BizException(
                ErrorCode.AI_ERROR.getCode(),
                "AI 未启用。请设置 NOTO_AI_ENABLED=true 和 AI_DASHSCOPE_API_KEY 后重启后端"
        );
    }
}
