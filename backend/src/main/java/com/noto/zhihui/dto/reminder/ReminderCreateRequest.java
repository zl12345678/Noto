package com.noto.zhihui.dto.reminder;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReminderCreateRequest {

    @NotNull
    private Long workspaceId;

    @NotNull
    private Long todoId;

    private String reminderType;

    @NotNull
    private LocalDateTime triggerAt;

    private String message;
}
