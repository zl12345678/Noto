package com.noto.zhihui.dto.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoCreateRequest {

    @NotNull
    private Long workspaceId;

    private Long noteId;

    @NotBlank
    private String title;

    private String description;

    private Integer priority;

    /** action | long_term */
    private String horizon;

    private LocalDateTime dueAt;
}
