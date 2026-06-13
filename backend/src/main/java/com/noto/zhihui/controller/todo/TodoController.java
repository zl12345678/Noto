package com.noto.zhihui.controller.todo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoStatusRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.vo.todo.TodoBoardVO;
import com.noto.zhihui.vo.todo.TodoVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.noto.zhihui.service.TodoExportService;

@RestController
@RequestMapping("/api/v1/todos")
public class TodoController {

    private final TodoService todoService;
    private final TodoExportService todoExportService;

    public TodoController(TodoService todoService, TodoExportService todoExportService) {
        this.todoService = todoService;
        this.todoExportService = todoExportService;
    }

    @GetMapping
    public ApiResponse<Page<TodoVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) Long noteId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String horizon
    ) {
        return ApiResponse.success(
                todoService.pageTodos(
                        requireUserId(), page, size, workspaceId, status, priority, noteId, keyword, horizon
                ),
                null
        );
    }

    @GetMapping("/board")
    public ApiResponse<TodoBoardVO> board(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(todoService.getTodoBoard(requireUserId(), workspaceId), null);
    }

    @GetMapping(value = "/export/ics", produces = "text/calendar; charset=UTF-8")
    public ResponseEntity<String> exportIcs(@RequestParam(required = false) Long workspaceId) {
        String ics = todoExportService.exportTodosAsIcs(requireUserId(), workspaceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"noto-todos.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics);
    }

    @GetMapping("/{id}")
    public ApiResponse<TodoVO> detail(@PathVariable Long id) {
        return ApiResponse.success(todoService.getTodoDetail(id, requireUserId()), null);
    }

    @PostMapping
    public ApiResponse<TodoVO> create(@Valid @RequestBody TodoCreateRequest request) {
        return ApiResponse.success(todoService.createTodo(request, requireUserId()), null);
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoVO> update(@PathVariable Long id, @Valid @RequestBody TodoUpdateRequest request) {
        return ApiResponse.success(todoService.updateTodo(id, request, requireUserId()), null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TodoVO> updateStatus(@PathVariable Long id, @Valid @RequestBody TodoStatusRequest request) {
        return ApiResponse.success(todoService.updateStatus(id, request.getStatus(), requireUserId()), null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        todoService.deleteTodo(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
