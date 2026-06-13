package com.noto.zhihui.vo.todo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TodoVO {

    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private Long noteId;
    private String noteTitle;
    private String title;
    private String description;
    private Integer priority;
    private Integer status;
    private String horizon;
    private LocalDateTime dueAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
