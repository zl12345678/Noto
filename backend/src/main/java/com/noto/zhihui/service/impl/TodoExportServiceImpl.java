package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.util.IcsCalendarBuilder;
import com.noto.zhihui.entity.TodoItemEntity;
import com.noto.zhihui.service.TodoExportService;
import com.noto.zhihui.service.TodoService;
import com.noto.zhihui.service.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class TodoExportServiceImpl implements TodoExportService {

    private final TodoService todoService;
    private final WorkspaceService workspaceService;

    public TodoExportServiceImpl(TodoService todoService, WorkspaceService workspaceService) {
        this.todoService = todoService;
        this.workspaceService = workspaceService;
    }

    @Override
    public String exportTodosAsIcs(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        List<TodoItemEntity> todos = todoService.lambdaQuery()
                .eq(TodoItemEntity::getCreatedBy, userId)
                .in(TodoItemEntity::getStatus, 0, 1)
                .isNotNull(TodoItemEntity::getDueAt)
                .eq(workspaceId != null, TodoItemEntity::getWorkspaceId, workspaceId)
                .orderByAsc(TodoItemEntity::getDueAt)
                .list();

        List<IcsCalendarBuilder.IcsEvent> events = new ArrayList<>();
        for (TodoItemEntity todo : todos) {
            String description = buildDescription(todo);
            events.add(new IcsCalendarBuilder.IcsEvent(
                    "todo-" + todo.getId() + "@noto-zhihui",
                    todo.getTitle(),
                    description,
                    todo.getDueAt(),
                    todo.getDueAt().plusHours(1)
            ));
        }
        String calendarName = workspaceId == null ? "Noto 待办" : "Noto 待办 · 知识库";
        return IcsCalendarBuilder.build(calendarName, events);
    }

    private String buildDescription(TodoItemEntity todo) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(todo.getDescription())) {
            builder.append(todo.getDescription().trim());
        }
        if (todo.getNoteId() != null) {
            if (!builder.isEmpty()) {
                builder.append("\\n");
            }
            builder.append("来源笔记 ID: ").append(todo.getNoteId());
        }
        return builder.toString();
    }
}
