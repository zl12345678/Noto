package com.noto.zhihui.agent;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量确认时传递上一步产物（noteId / todoId）。
 */
@Getter
@Setter
public class AgentConfirmChain {

    private Long noteId;
    private final List<Long> todoIds = new ArrayList<>();

    public void addTodoIds(List<Long> ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id != null) {
                todoIds.add(id);
            }
        }
    }

    public Long resolveTodoId(Long todoId, String todoTitle) {
        if (todoId != null && todoId > 0) {
            return todoId;
        }
        if (!todoIds.isEmpty()) {
            return todoIds.get(0);
        }
        return null;
    }
}
