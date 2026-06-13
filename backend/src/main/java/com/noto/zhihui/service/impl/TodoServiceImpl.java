package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.constants.TodoHorizon;
import com.noto.zhihui.common.util.MarkdownTodoExtractor;
import com.noto.zhihui.common.util.MarkdownTodoExtractor.ExtractedTodoItem;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.ConfirmExtractTodoItem;
import com.noto.zhihui.dto.ai.ConfirmExtractTodosRequest;
import com.noto.zhihui.dto.todo.TodoCreateRequest;
import com.noto.zhihui.dto.todo.TodoUpdateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.mapper.TodoMapper;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.note.ExtractedTodoSuggestionVO;
import com.noto.zhihui.vo.note.NoteExtractTodosPreviewVO;
import com.noto.zhihui.vo.note.NoteExtractTodosVO;
import com.noto.zhihui.vo.todo.TodoBoardVO;
import com.noto.zhihui.vo.todo.TodoVO;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, TodoItemEntity> implements TodoService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_IN_PROGRESS = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_CANCELLED = 3;

    private final WorkspaceService workspaceService;
    private final NoteService noteService;

    public TodoServiceImpl(WorkspaceService workspaceService, NoteService noteService) {
        this.workspaceService = workspaceService;
        this.noteService = noteService;
    }

    @Override
    public Page<TodoVO> pageTodos(
            Long userId,
            long page,
            long size,
            Long workspaceId,
            Integer status,
            Integer priority,
            Long noteId,
            String keyword,
            String horizon
    ) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }

        String normalizedHorizon = horizon == null ? null : TodoHorizon.normalize(horizon);
        LambdaQueryWrapper<TodoItemEntity> wrapper = new LambdaQueryWrapper<TodoItemEntity>()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(status != null, TodoItemEntity::getStatus, status)
                .eq(priority != null, TodoItemEntity::getPriority, priority)
                .eq(noteId != null, TodoItemEntity::getNoteId, noteId)
                .eq(normalizedHorizon != null, TodoItemEntity::getHorizon, normalizedHorizon)
                .orderByAsc(TodoItemEntity::getStatus)
                .orderByDesc(TodoItemEntity::getPriority)
                .orderByAsc(TodoItemEntity::getDueAt)
                .orderByDesc(TodoItemEntity::getUpdatedAt);

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                    .like(TodoItemEntity::getTitle, trimmed)
                    .or()
                    .like(TodoItemEntity::getDescription, trimmed));
        }

        Page<TodoItemEntity> entityPage = page(new Page<>(page, size), wrapper);
        return toVOPage(entityPage);
    }

    @Override
    public TodoBoardVO getTodoBoard(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        List<TodoVO> actionTodos = listActionTodos(userId, workspaceId, 12);
        List<TodoVO> parallelTodos = listParallelTodos(userId, workspaceId, 12);
        long parallelActiveCount = countParallelTodos(userId, workspaceId);
        long longTermActiveCount = countLongTermActive(userId, workspaceId);
        return new TodoBoardVO(
                actionTodos,
                parallelTodos,
                List.of(),
                longTermActiveCount,
                parallelActiveCount,
                countTodayCompletedTodos(userId)
        );
    }

    @Override
    public TodoVO getTodoDetail(Long id, Long userId) {
        return toVO(requireOwnedTodo(id, userId));
    }

    @Override
    @Transactional
    public TodoVO createTodo(TodoCreateRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        validateNote(request.getWorkspaceId(), request.getNoteId(), userId);

        TodoItemEntity todo = new TodoItemEntity();
        todo.setWorkspaceId(request.getWorkspaceId());
        todo.setNoteId(request.getNoteId());
        todo.setTitle(request.getTitle().trim());
        todo.setDescription(trimDescription(request.getDescription()));
        todo.setPriority(resolvePriority(request.getPriority()));
        todo.setHorizon(TodoHorizon.normalize(request.getHorizon()));
        todo.setStatus(STATUS_PENDING);
        todo.setDueAt(request.getDueAt());
        todo.setCreatedBy(userId);
        save(todo);
        return toVO(todo);
    }

    @Override
    @Transactional
    public TodoVO updateTodo(Long id, TodoUpdateRequest request, Long userId) {
        TodoItemEntity todo = requireOwnedTodo(id, userId);
        if (request.getNoteId() != null) {
            validateNote(todo.getWorkspaceId(), request.getNoteId(), userId);
            todo.setNoteId(request.getNoteId());
        }
        todo.setTitle(request.getTitle().trim());
        todo.setDescription(trimDescription(request.getDescription()));
        todo.setPriority(resolvePriority(request.getPriority()));
        if (StringUtils.hasText(request.getHorizon())) {
            todo.setHorizon(TodoHorizon.normalize(request.getHorizon()));
        }
        todo.setDueAt(request.getDueAt());
        updateById(todo);
        return toVO(todo);
    }

    @Override
    @Transactional
    public TodoVO updateStatus(Long id, int status, Long userId) {
        if (status < 0 || status > STATUS_CANCELLED) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        TodoItemEntity todo = requireOwnedTodo(id, userId);
        todo.setStatus(status);
        if (status == STATUS_COMPLETED) {
            todo.setCompletedAt(LocalDateTime.now());
        } else {
            todo.setCompletedAt(null);
        }
        updateById(todo);
        return toVO(todo);
    }

    @Override
    @Transactional
    public void deleteTodo(Long id, Long userId) {
        requireOwnedTodo(id, userId);
        removeById(id);
    }

    @Override
    public NoteExtractTodosPreviewVO previewMarkdownExtractTodosFromNote(Long noteId, Long userId) {
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        List<ExtractedTodoItem> extracted = MarkdownTodoExtractor.extract(note.getContent());
        return previewExtractedTodos(noteId, userId, extracted);
    }

    @Override
    @Transactional
    public NoteExtractTodosVO confirmExtractTodosFromNote(Long noteId, Long userId, ConfirmExtractTodosRequest request) {
        noteService.requireOwnedNote(noteId, userId);
        List<ExtractedTodoItem> items = request.getItems().stream()
                .map(this::toExtractedItem)
                .toList();
        return persistExtractedTodos(noteId, userId, items);
    }

    private ExtractedTodoItem toExtractedItem(ConfirmExtractTodoItem item) {
        return new ExtractedTodoItem(
                item.getTitle().trim(),
                item.isCompleted(),
                item.getPriority(),
                item.getHorizon(),
                item.getDueAt()
        );
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractedTodos(Long noteId, Long userId, List<ExtractedTodoItem> extracted) {
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        if (extracted == null || extracted.isEmpty()) {
            return new NoteExtractTodosPreviewVO(noteId, note.getTitle(), List.of());
        }
        Set<String> existingTitles = loadExistingTodoTitles(userId, noteId);
        List<ExtractedTodoSuggestionVO> suggestions = new ArrayList<>();
        Set<String> seenInBatch = new LinkedHashSet<>();
        for (ExtractedTodoItem item : extracted) {
            String title = item.title() == null ? "" : item.title().trim();
            if (title.isBlank()) {
                continue;
            }
            String key = title.toLowerCase();
            if (!seenInBatch.add(key)) {
                continue;
            }
            suggestions.add(new ExtractedTodoSuggestionVO(
                    title,
                    item.completed(),
                    item.resolvedPriority(),
                    item.resolvedHorizon(),
                    item.resolvedDueAt(),
                    existingTitles.contains(key)
            ));
        }
        return new NoteExtractTodosPreviewVO(noteId, note.getTitle(), suggestions);
    }

    @Override
    public NoteExtractTodosPreviewVO previewExtractedTodosFromDraft(String noteTitle, List<ExtractedTodoItem> extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return new NoteExtractTodosPreviewVO(null, noteTitle, List.of());
        }
        List<ExtractedTodoSuggestionVO> suggestions = new ArrayList<>();
        Set<String> seenInBatch = new LinkedHashSet<>();
        for (ExtractedTodoItem item : extracted) {
            String title = item.title() == null ? "" : item.title().trim();
            if (title.isBlank()) {
                continue;
            }
            String key = title.toLowerCase();
            if (!seenInBatch.add(key)) {
                continue;
            }
            suggestions.add(new ExtractedTodoSuggestionVO(
                    title,
                    item.completed(),
                    item.resolvedPriority(),
                    item.resolvedHorizon(),
                    item.resolvedDueAt(),
                    false
            ));
        }
        return new NoteExtractTodosPreviewVO(null, noteTitle, suggestions);
    }

    @Override
    @Transactional
    public NoteExtractTodosVO persistExtractedTodos(Long noteId, Long userId, List<ExtractedTodoItem> extracted) {
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        if (extracted == null || extracted.isEmpty()) {
            return new NoteExtractTodosVO(0, 0, List.of());
        }

        Set<String> existingTitles = loadExistingTodoTitles(userId, noteId);

        List<TodoVO> created = new ArrayList<>();
        int skipped = 0;
        for (ExtractedTodoItem item : extracted) {
            String normalizedTitle = item.title().trim().toLowerCase();
            if (existingTitles.contains(normalizedTitle)) {
                skipped++;
                continue;
            }
            TodoItemEntity todo = new TodoItemEntity();
            todo.setWorkspaceId(note.getWorkspaceId());
            todo.setNoteId(noteId);
            todo.setTitle(item.title().trim());
            todo.setDescription("从文档《" + note.getTitle() + "》提取");
            todo.setPriority(item.resolvedPriority());
            todo.setHorizon(item.resolvedHorizon());
            todo.setDueAt(item.resolvedDueAt());
            todo.setStatus(item.completed() ? STATUS_COMPLETED : STATUS_PENDING);
            if (item.completed()) {
                todo.setCompletedAt(LocalDateTime.now());
            }
            todo.setCreatedBy(userId);
            save(todo);
            existingTitles.add(normalizedTitle);
            created.add(toVO(todo));
        }
        return new NoteExtractTodosVO(created.size(), skipped, created);
    }

    @Override
    public List<TodoVO> listActionTodos(Long userId, Long workspaceId, int limit) {
        int resolvedLimit = limit <= 0 ? 8 : Math.min(limit, 20);

        List<TodoItemEntity> records = lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(TodoItemEntity::getStatus, STATUS_PENDING)
                .list();

        Map<Long, String> workspaceNames = loadWorkspaceNames(records);
        Map<Long, String> noteTitles = loadNoteTitles(records);

        return records.stream()
                .sorted(this::compareByDueAtAsc)
                .limit(resolvedLimit)
                .map(item -> toVO(item, workspaceNames, noteTitles))
                .toList();
    }

    private List<TodoVO> listParallelTodos(Long userId, Long workspaceId, int limit) {
        int resolvedLimit = limit <= 0 ? 12 : Math.min(limit, 30);
        List<TodoItemEntity> records = lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(TodoItemEntity::getStatus, STATUS_IN_PROGRESS)
                .list();
        return toVOList(records.stream().sorted(this::compareByDueAtAsc).limit(resolvedLimit).toList());
    }

    private List<TodoVO> listLongTermTodos(Long userId, Long workspaceId, int limit) {
        int resolvedLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        List<TodoItemEntity> records = lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(TodoItemEntity::getHorizon, TodoHorizon.LONG_TERM)
                .in(TodoItemEntity::getStatus, STATUS_PENDING, STATUS_IN_PROGRESS)
                .list();
        return toVOList(records.stream().sorted(this::compareByDueAtAsc).limit(resolvedLimit).toList());
    }

    private long countParallelTodos(Long userId, Long workspaceId) {
        return lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(TodoItemEntity::getStatus, STATUS_IN_PROGRESS)
                .count();
    }

    private long countLongTermActive(Long userId, Long workspaceId) {
        return lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .eq(TodoItemEntity::getHorizon, TodoHorizon.LONG_TERM)
                .in(TodoItemEntity::getStatus, STATUS_PENDING, STATUS_IN_PROGRESS)
                .count();
    }

    private List<TodoVO> toVOList(List<TodoItemEntity> records) {
        Map<Long, String> workspaceNames = loadWorkspaceNames(records);
        Map<Long, String> noteTitles = loadNoteTitles(records);
        return records.stream()
                .map(item -> toVO(item, workspaceNames, noteTitles))
                .toList();
    }

    @Override
    public long countTodayCompletedTodos(Long userId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(TodoItemEntity::getStatus, STATUS_COMPLETED)
                .ge(TodoItemEntity::getCompletedAt, startOfDay)
                .count();
    }

    @Override
    public List<TodoVO> listTodayCompletedTodos(Long userId, Long workspaceId, int limit) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        int size = Math.min(Math.max(limit, 1), 20);
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<TodoItemEntity> records = lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(TodoItemEntity::getStatus, STATUS_COMPLETED)
                .ge(TodoItemEntity::getCompletedAt, startOfDay)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .orderByDesc(TodoItemEntity::getCompletedAt)
                .last("LIMIT " + size)
                .list();
        return toVOList(records);
    }

    /** 看板按截止时间升序；无截止时间的排在后面 */
    private int compareByDueAtAsc(TodoItemEntity left, TodoItemEntity right) {
        if (left.getDueAt() == null && right.getDueAt() == null) {
            return compareByPriorityThenUpdated(left, right);
        }
        if (left.getDueAt() == null) {
            return 1;
        }
        if (right.getDueAt() == null) {
            return -1;
        }
        int dueCompare = left.getDueAt().compareTo(right.getDueAt());
        if (dueCompare != 0) {
            return dueCompare;
        }
        return compareByPriorityThenUpdated(left, right);
    }

    private int compareByPriorityThenUpdated(TodoItemEntity left, TodoItemEntity right) {
        int priorityCompare = Integer.compare(
                left.getPriority() == null ? 2 : left.getPriority(),
                right.getPriority() == null ? 2 : right.getPriority()
        );
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        if (left.getUpdatedAt() != null && right.getUpdatedAt() != null) {
            return left.getUpdatedAt().compareTo(right.getUpdatedAt());
        }
        return 0;
    }

    private TodoItemEntity requireOwnedTodo(Long id, Long userId) {
        TodoItemEntity todo = getById(id);
        if (todo == null || !userId.equals(todo.getCreatedBy())) {
            throw new BizException(ErrorCode.TODO_NOT_FOUND);
        }
        workspaceService.requireOwnedWorkspace(todo.getWorkspaceId(), userId);
        return todo;
    }

    private void validateNote(Long workspaceId, Long noteId, Long userId) {
        if (noteId == null) {
            return;
        }
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        if (!workspaceId.equals(note.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    private Set<String> loadExistingTodoTitles(Long userId, Long noteId) {
        return lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .eq(TodoItemEntity::getNoteId, noteId)
                .list()
                .stream()
                .map(TodoItemEntity::getTitle)
                .filter(Objects::nonNull)
                .map(title -> title.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    private String resolveHorizonValue(String horizon) {
        return TodoHorizon.normalize(horizon);
    }

    private int resolvePriority(Integer priority) {
        if (priority == null || priority < 1 || priority > 3) {
            return 2;
        }
        return priority;
    }

    private String trimDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Page<TodoVO> toVOPage(Page<TodoItemEntity> entityPage) {
        List<TodoItemEntity> records = entityPage.getRecords();
        Map<Long, String> workspaceNames = loadWorkspaceNames(records);
        Map<Long, String> noteTitles = loadNoteTitles(records);

        Page<TodoVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records.stream()
                .map(item -> toVO(item, workspaceNames, noteTitles))
                .toList());
        return voPage;
    }

    private TodoVO toVO(TodoItemEntity todo) {
        Map<Long, String> workspaceNames = loadWorkspaceNames(List.of(todo));
        Map<Long, String> noteTitles = loadNoteTitles(List.of(todo));
        return toVO(todo, workspaceNames, noteTitles);
    }

    private TodoVO toVO(TodoItemEntity todo, Map<Long, String> workspaceNames, Map<Long, String> noteTitles) {
        return new TodoVO(
                todo.getId(),
                todo.getWorkspaceId(),
                workspaceNames.get(todo.getWorkspaceId()),
                todo.getNoteId(),
                todo.getNoteId() == null ? null : noteTitles.get(todo.getNoteId()),
                todo.getTitle(),
                todo.getDescription(),
                todo.getPriority(),
                todo.getStatus(),
                resolveHorizonValue(todo.getHorizon()),
                todo.getDueAt(),
                todo.getCompletedAt(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }

    private Map<Long, String> loadWorkspaceNames(List<TodoItemEntity> records) {
        List<Long> ids = records.stream()
                .map(TodoItemEntity::getWorkspaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workspaceService.listByIds(ids).stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, WorkspaceEntity::getName, (a, b) -> a));
    }

    private Map<Long, String> loadNoteTitles(List<TodoItemEntity> records) {
        List<Long> ids = records.stream()
                .map(TodoItemEntity::getNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return noteService.listByIds(ids).stream()
                .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getTitle, (a, b) -> a));
    }
}
