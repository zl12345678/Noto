package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.reminder.ReminderUpdateRequest;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.ReminderEntity;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.mapper.ReminderMapper;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.ReminderService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.reminder.ReminderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReminderServiceImpl extends ServiceImpl<ReminderMapper, ReminderEntity> implements ReminderService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SENT = 1;
    private static final int STATUS_CANCELLED = 2;

    private final WorkspaceService workspaceService;
    private final TodoService todoService;
    private final NoteService noteService;

    public ReminderServiceImpl(WorkspaceService workspaceService, TodoService todoService, NoteService noteService) {
        this.workspaceService = workspaceService;
        this.todoService = todoService;
        this.noteService = noteService;
    }

    @Override
    public Page<ReminderVO> pageReminders(
            Long userId,
            long page,
            long size,
            Long workspaceId,
            Integer status,
            Long todoId
    ) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }

        LambdaQueryWrapper<ReminderEntity> wrapper = new LambdaQueryWrapper<ReminderEntity>()
                .eq(ReminderEntity::getCreatedBy, userId)
                .eq(workspaceId != null, ReminderEntity::getWorkspaceId, workspaceId)
                .eq(status != null, ReminderEntity::getStatus, status)
                .eq(todoId != null, ReminderEntity::getTodoId, todoId)
                .orderByAsc(ReminderEntity::getStatus)
                .orderByAsc(ReminderEntity::getTriggerAt);

        Page<ReminderEntity> entityPage = page(new Page<>(page, size), wrapper);
        return toVOPage(entityPage);
    }

    @Override
    @Transactional
    public ReminderVO createReminder(ReminderCreateRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        TodoItemEntity todo = todoService.getById(request.getTodoId());
        if (todo == null || !userId.equals(todo.getCreatedBy())) {
            throw new BizException(ErrorCode.TODO_NOT_FOUND);
        }
        if (!request.getWorkspaceId().equals(todo.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        ReminderEntity reminder = new ReminderEntity();
        reminder.setWorkspaceId(request.getWorkspaceId());
        reminder.setTodoId(request.getTodoId());
        reminder.setReminderType(resolveReminderType(request.getReminderType()));
        reminder.setTriggerAt(request.getTriggerAt());
        reminder.setMessage(trimMessage(request.getMessage(), todo.getTitle()));
        reminder.setStatus(STATUS_PENDING);
        reminder.setCreatedBy(userId);
        save(reminder);
        return toVO(reminder);
    }

    @Override
    @Transactional
    public ReminderVO updateReminder(Long id, ReminderUpdateRequest request, Long userId) {
        ReminderEntity reminder = requireOwnedReminder(id, userId);
        if (reminder.getStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        if (request.getReminderType() != null && !request.getReminderType().isBlank()) {
            reminder.setReminderType(resolveReminderType(request.getReminderType()));
        }
        if (request.getTriggerAt() != null) {
            reminder.setTriggerAt(request.getTriggerAt());
        }
        if (request.getMessage() != null) {
            reminder.setMessage(trimMessage(request.getMessage(), null));
        }
        updateById(reminder);
        return toVO(reminder);
    }

    @Override
    @Transactional
    public ReminderVO cancelReminder(Long id, Long userId) {
        ReminderEntity reminder = requireOwnedReminder(id, userId);
        if (reminder.getStatus() == STATUS_SENT) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        reminder.setStatus(STATUS_CANCELLED);
        updateById(reminder);
        return toVO(reminder);
    }

    @Override
    @Transactional
    public void deleteReminder(Long id, Long userId) {
        requireOwnedReminder(id, userId);
        removeById(id);
    }

    @Override
    @Transactional
    public List<ReminderVO> deliverDueReminders(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<ReminderEntity> wrapper = new LambdaQueryWrapper<ReminderEntity>()
                .eq(ReminderEntity::getCreatedBy, userId)
                .eq(ReminderEntity::getStatus, STATUS_PENDING)
                .le(ReminderEntity::getTriggerAt, now)
                .orderByAsc(ReminderEntity::getTriggerAt);

        List<ReminderEntity> dueReminders = list(wrapper);
        if (dueReminders.isEmpty()) {
            return List.of();
        }

        for (ReminderEntity reminder : dueReminders) {
            reminder.setStatus(STATUS_SENT);
            reminder.setSentAt(now);
        }
        updateBatchById(dueReminders);

        Map<Long, String> workspaceNames = loadWorkspaceNames(dueReminders);
        Map<Long, TodoItemEntity> todos = loadTodos(dueReminders);
        Map<Long, NoteEntity> notes = loadNotesForTodos(todos);
        return dueReminders.stream()
                .map(item -> toVO(item, workspaceNames, todos, notes))
                .toList();
    }

    private ReminderEntity requireOwnedReminder(Long id, Long userId) {
        ReminderEntity reminder = getById(id);
        if (reminder == null || !userId.equals(reminder.getCreatedBy())) {
            throw new BizException(ErrorCode.REMINDER_NOT_FOUND);
        }
        workspaceService.requireOwnedWorkspace(reminder.getWorkspaceId(), userId);
        return reminder;
    }

    private String resolveReminderType(String type) {
        if (type == null || type.isBlank()) {
            return "once";
        }
        return type.trim();
    }

    private String trimMessage(String message, String fallbackTitle) {
        if (message != null && !message.isBlank()) {
            return message.trim();
        }
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return "待办提醒：" + fallbackTitle.trim();
        }
        return "待办提醒";
    }

    private Page<ReminderVO> toVOPage(Page<ReminderEntity> entityPage) {
        List<ReminderEntity> records = entityPage.getRecords();
        Map<Long, String> workspaceNames = loadWorkspaceNames(records);
        Map<Long, TodoItemEntity> todos = loadTodos(records);
        Map<Long, NoteEntity> notes = loadNotesForTodos(todos);

        Page<ReminderVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records.stream()
                .map(item -> toVO(item, workspaceNames, todos, notes))
                .toList());
        return voPage;
    }

    private ReminderVO toVO(ReminderEntity reminder) {
        Map<Long, String> workspaceNames = loadWorkspaceNames(List.of(reminder));
        Map<Long, TodoItemEntity> todos = loadTodos(List.of(reminder));
        Map<Long, NoteEntity> notes = loadNotesForTodos(todos);
        return toVO(reminder, workspaceNames, todos, notes);
    }

    private ReminderVO toVO(
            ReminderEntity reminder,
            Map<Long, String> workspaceNames,
            Map<Long, TodoItemEntity> todos,
            Map<Long, NoteEntity> notes
    ) {
        TodoItemEntity todo = todos.get(reminder.getTodoId());
        Long noteId = todo != null ? todo.getNoteId() : null;
        String noteTitle = null;
        String noteContext = null;
        if (noteId != null) {
            NoteEntity note = notes.get(noteId);
            if (note != null) {
                noteTitle = note.getTitle();
                noteContext = buildNoteContext(note);
            }
        }
        return new ReminderVO(
                reminder.getId(),
                reminder.getWorkspaceId(),
                workspaceNames.get(reminder.getWorkspaceId()),
                reminder.getTodoId(),
                todo != null ? todo.getTitle() : null,
                reminder.getReminderType(),
                reminder.getTriggerAt(),
                reminder.getMessage(),
                reminder.getStatus(),
                reminder.getSentAt(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt(),
                noteId,
                noteTitle,
                noteContext
        );
    }

    private Map<Long, String> loadWorkspaceNames(List<ReminderEntity> records) {
        List<Long> ids = records.stream()
                .map(ReminderEntity::getWorkspaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workspaceService.listByIds(ids).stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, WorkspaceEntity::getName, (a, b) -> a));
    }

    private Map<Long, TodoItemEntity> loadTodos(List<ReminderEntity> records) {
        List<Long> ids = records.stream()
                .map(ReminderEntity::getTodoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return todoService.listByIds(ids).stream()
                .collect(Collectors.toMap(TodoItemEntity::getId, item -> item, (a, b) -> a));
    }

    private Map<Long, NoteEntity> loadNotesForTodos(Map<Long, TodoItemEntity> todos) {
        List<Long> noteIds = todos.values().stream()
                .map(TodoItemEntity::getNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        return noteService.listByIds(noteIds).stream()
                .collect(Collectors.toMap(NoteEntity::getId, item -> item, (a, b) -> a));
    }

    private String buildNoteContext(NoteEntity note) {
        if (StringUtils.hasText(note.getSummary())) {
            return NoteTextUtils.excerpt(note.getSummary(), 280);
        }
        return NoteTextUtils.excerpt(note.getContent(), 280);
    }
}
