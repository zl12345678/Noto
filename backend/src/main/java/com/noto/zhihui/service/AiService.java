package com.noto.zhihui.service;

import com.noto.zhihui.dto.ai.AiAskRequest;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.ai.NoteAiDraftRequest;
import com.noto.zhihui.dto.ai.NoteSelectionTransformRequest;
import com.noto.zhihui.dto.ai.NoteSynthesizeRequest;
import com.noto.zhihui.dto.ai.NoteTransformRequest;
import com.noto.zhihui.vo.ai.NoteSynthesizeVO;
import com.noto.zhihui.vo.ai.NoteTransformVO;
import com.noto.zhihui.vo.ai.AiAskVO;
import com.noto.zhihui.vo.ai.AiStatusVO;
import com.noto.zhihui.vo.ai.NoteSummaryVO;
import com.noto.zhihui.dto.ai.TodoBreakdownRequest;
import com.noto.zhihui.dto.ai.TodoCompletionRetroRequest;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionsVO;
import com.noto.zhihui.vo.ai.TodoBreakdownVO;
import com.noto.zhihui.vo.ai.TodoCompletionRetroVO;
import com.noto.zhihui.vo.ai.TodoOverdueAdviceVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;

public interface AiService {

    AiStatusVO status();

    NoteSummaryVO summarizeNote(Long noteId, Long userId, NoteAiDraftRequest draft);

    NoteTransformVO transformNote(Long noteId, Long userId, NoteTransformRequest request);

    void transformSelectionStream(
            Long noteId,
            NoteSelectionTransformRequest request,
            Long userId,
            AiStreamSink sink
    );

    AiAskVO ask(AiAskRequest request, Long userId);

    void askStream(AiAskRequest request, Long userId, AiStreamSink sink);

    NoteExtractTodosPreviewVO previewExtractTodosFromNote(Long noteId, Long userId);

    NoteExtractTodosPreviewVO previewExtractTodosFromContent(
            String title,
            String content,
            Long workspaceId,
            Long userId
    );

    NoteExtractTodosVO confirmExtractTodosFromNote(Long noteId, Long userId, ConfirmExtractTodosRequest request);

    AiDailySuggestionsVO suggestDailyActions(Long userId, Long workspaceId);

    AiDailyReviewVO reviewDailyProgress(Long userId, Long workspaceId);

    TodoBreakdownVO breakdownTodo(TodoBreakdownRequest request, Long userId);

    NoteSynthesizeVO synthesizeNotes(NoteSynthesizeRequest request, Long userId);

    TodoOverdueAdviceVO suggestOverdueTodoActions(Long userId, Long workspaceId);

    TodoCompletionRetroVO completionRetro(Long todoId, TodoCompletionRetroRequest request, Long userId);
}
