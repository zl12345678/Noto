package com.noto.zhihui.controller.note;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.note.NoteClipImportRequest;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteFavoriteRequest;
import com.noto.zhihui.dto.note.NoteStatusRequest;
import com.noto.zhihui.dto.note.NoteTreeMoveRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.NoteImportService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.vo.note.NoteImportVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.vo.note.NoteVO;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;
    private final TodoService todoService;
    private final NoteImportService noteImportService;

    public NoteController(NoteService noteService, TodoService todoService, NoteImportService noteImportService) {
        this.noteService = noteService;
        this.todoService = todoService;
        this.noteImportService = noteImportService;
    }

    @GetMapping
    public ApiResponse<Page<NoteVO>> list(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Boolean isFavorite,
                                          @RequestParam(required = false) Long workspaceId,
                                          @RequestParam(required = false) Long folderId,
                                          @RequestParam(required = false) Long tagId) {
        Long userId = requireUserId();
        return ApiResponse.success(
                noteService.pageNotes(userId, page, size, keyword, status, isFavorite, workspaceId, folderId, tagId, true),
                null
        );
    }

    @PostMapping("/import")
    public ApiResponse<NoteImportVO> importClip(@Valid @RequestBody NoteClipImportRequest request) {
        return ApiResponse.success(noteImportService.importClip(request, requireUserId()), null);
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteVO> detail(@PathVariable Long id) {
        return ApiResponse.success(noteService.getNoteDetail(id, requireUserId()), null);
    }

    @PostMapping
    public ApiResponse<NoteVO> create(@Valid @RequestBody NoteCreateRequest request) {
        return ApiResponse.success(noteService.createNote(request, requireUserId()), null);
    }

    @PutMapping("/{id}")
    public ApiResponse<NoteVO> update(@PathVariable Long id, @Valid @RequestBody NoteUpdateRequest request) {
        return ApiResponse.success(noteService.updateNote(id, request, requireUserId()), null);
    }

    @PatchMapping("/{id}/favorite")
    public ApiResponse<NoteVO> favorite(@PathVariable Long id, @Valid @RequestBody NoteFavoriteRequest request) {
        return ApiResponse.success(noteService.updateFavorite(id, request.getIsFavorite(), requireUserId()), null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<NoteVO> status(@PathVariable Long id, @Valid @RequestBody NoteStatusRequest request) {
        return ApiResponse.success(noteService.updateStatus(id, request.getStatus(), requireUserId()), null);
    }

    @PostMapping("/{id}/extract-todos")
    public ApiResponse<NoteExtractTodosPreviewVO> extractTodos(@PathVariable Long id) {
        return ApiResponse.success(todoService.previewMarkdownExtractTodosFromNote(id, requireUserId()), null);
    }

    @PostMapping("/{id}/extract-todos/confirm")
    public ApiResponse<NoteExtractTodosVO> confirmExtractTodos(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmExtractTodosRequest request
    ) {
        return ApiResponse.success(todoService.confirmExtractTodosFromNote(id, requireUserId(), request), null);
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<NoteVO>> related(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ApiResponse.success(noteService.listRelatedNotes(id, requireUserId(), limit), null);
    }

    @PatchMapping("/{id}/tree")
    public ApiResponse<NoteVO> moveTree(@PathVariable Long id, @Valid @RequestBody NoteTreeMoveRequest request) {
        return ApiResponse.success(noteService.moveNoteInTree(id, request, requireUserId()), null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.deleteNote(id, requireUserId());
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
