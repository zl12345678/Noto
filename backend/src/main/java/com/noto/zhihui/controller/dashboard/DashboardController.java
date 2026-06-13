package com.noto.zhihui.controller.dashboard;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.dashboard.DashboardStatsVO;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.tag.TagVO;
import com.noto.zhihui.vo.todo.TodoBoardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final NoteService noteService;
    private final NoteTagService noteTagService;
    private final TodoService todoService;
    private final WorkspaceService workspaceService;

    public DashboardController(
            NoteService noteService,
            NoteTagService noteTagService,
            TodoService todoService,
            WorkspaceService workspaceService
    ) {
        this.noteService = noteService;
        this.noteTagService = noteTagService;
        this.todoService = todoService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsVO> stats() {
        Long userId = requireUserId();
        Set<Long> excludedHomeNoteIds = listExcludedHomeNoteIds(userId);

        var noteQuery = noteService.lambdaQuery().eq(NoteEntity::getCreatedBy, userId);
        if (!excludedHomeNoteIds.isEmpty()) {
            noteQuery.notIn(NoteEntity::getId, excludedHomeNoteIds);
        }
        long totalNotes = noteQuery.count();

        var favoriteQuery = noteService.lambdaQuery()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(NoteEntity::getIsFavorite, true);
        if (!excludedHomeNoteIds.isEmpty()) {
            favoriteQuery.notIn(NoteEntity::getId, excludedHomeNoteIds);
        }
        long favoriteNotes = favoriteQuery.count();

        var archivedQuery = noteService.lambdaQuery()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(NoteEntity::getStatus, 1);
        if (!excludedHomeNoteIds.isEmpty()) {
            archivedQuery.notIn(NoteEntity::getId, excludedHomeNoteIds);
        }
        long archivedNotes = archivedQuery.count();

        var recentQuery = noteService.lambdaQuery()
                .eq(NoteEntity::getCreatedBy, userId)
                .orderByDesc(NoteEntity::getLastEditedAt)
                .last("LIMIT 5");
        if (!excludedHomeNoteIds.isEmpty()) {
            recentQuery.notIn(NoteEntity::getId, excludedHomeNoteIds);
        }
        List<NoteEntity> recentEntities = recentQuery.list();
        var tagMap = noteTagService.listTagsByNoteIds(recentEntities.stream().map(NoteEntity::getId).toList());
        List<NoteVO> recentNotes = recentEntities.stream()
                .map(note -> toVO(note, tagMap.getOrDefault(note.getId(), List.of())))
                .toList();
        long totalTodos = todoService.lambdaQuery().eq(TodoItemEntity::getCreatedBy, userId).count();
        long pendingTodos = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .count();
        long completedTodos = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(TodoItemEntity::getStatus, 2)
                .count();
        long overdueTodos = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .isNotNull(TodoItemEntity::getDueAt)
                .lt(TodoItemEntity::getDueAt, LocalDateTime.now())
                .count();
        TodoBoardVO board = todoService.getTodoBoard(userId, null);
        boolean onboardingEligible = totalNotes == 0
                && pendingTodos == 0
                && board.getActionTodos().isEmpty()
                && board.getParallelTodos().isEmpty();
        return ApiResponse.success(new DashboardStatsVO(
                totalNotes,
                favoriteNotes,
                archivedNotes,
                totalTodos,
                pendingTodos,
                completedTodos,
                overdueTodos,
                recentNotes,
                board.getActionTodos(),
                board.getParallelTodos(),
                board.getLongTermTodos(),
                board.getParallelActiveCount(),
                board.getLongTermActiveCount(),
                board.getTodayCompletedTodos(),
                onboardingEligible
        ), null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 各知识库自动生成的首页文档（含历史未绑定 home_note_id 的数据），不计入用户知识输入 */
    private Set<Long> listExcludedHomeNoteIds(Long userId) {
        Set<Long> ids = workspaceService.lambdaQuery()
                .eq(WorkspaceEntity::getOwnerUserId, userId)
                .isNotNull(WorkspaceEntity::getHomeNoteId)
                .list()
                .stream()
                .map(WorkspaceEntity::getHomeNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        noteService.lambdaQuery()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(NoteEntity::getTitle, "首页")
                .eq(NoteEntity::getSummary, "知识库首页")
                .list()
                .forEach(note -> ids.add(note.getId()));
        return ids;
    }

    private NoteVO toVO(NoteEntity note, List<TagVO> tags) {
        return new NoteVO(
                note.getId(),
                note.getWorkspaceId(),
                note.getFolderId(),
                note.getParentId(),
                note.getSortOrder() == null ? 0 : note.getSortOrder(),
                note.getTitle(),
                null,
                NoteTextUtils.excerpt(note.getContent(), 120),
                note.getContentType(),
                note.getSummary(),
                note.getStatus(),
                note.getIsFavorite(),
                note.getLastEditedAt(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                tags
        );
    }
}
