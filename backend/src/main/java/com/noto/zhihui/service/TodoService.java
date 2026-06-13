package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.common.util.MarkdownTodoExtractor.ExtractedTodoItem;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.vo.todo.TodoBoardVO;
import com.noto.zhihui.vo.todo.TodoVO;

import java.util.List;

public interface TodoService extends IService<TodoItemEntity> {

    Page<TodoVO> pageTodos(
            Long userId,
            long page,
            long size,
            Long workspaceId,
            Integer status,
            Integer priority,
            Long noteId,
            String keyword,
            String horizon
    );

    TodoBoardVO getTodoBoard(Long userId, Long workspaceId);

    TodoVO getTodoDetail(Long id, Long userId);

    TodoVO createTodo(TodoCreateRequest request, Long userId);

    TodoVO updateTodo(Long id, TodoUpdateRequest request, Long userId);

    TodoVO updateStatus(Long id, int status, Long userId);

    void deleteTodo(Long id, Long userId);

    NoteExtractTodosPreviewVO previewMarkdownExtractTodosFromNote(Long noteId, Long userId);

    NoteExtractTodosVO confirmExtractTodosFromNote(Long noteId, Long userId, ConfirmExtractTodosRequest request);

    NoteExtractTodosPreviewVO previewExtractedTodos(Long noteId, Long userId, List<ExtractedTodoItem> extracted);

    NoteExtractTodosPreviewVO previewExtractedTodosFromDraft(String noteTitle, List<ExtractedTodoItem> extracted);

    NoteExtractTodosVO persistExtractedTodos(Long noteId, Long userId, List<ExtractedTodoItem> extracted);

    List<TodoVO> listActionTodos(Long userId, Long workspaceId, int limit);

    List<TodoVO> listTodayCompletedTodos(Long userId, Long workspaceId, int limit);

    long countTodayCompletedTodos(Long userId);
}
