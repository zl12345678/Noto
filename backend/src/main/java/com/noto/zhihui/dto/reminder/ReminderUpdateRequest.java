package com.noto.zhihui.dto.reminder;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReminderUpdateRequest {

    private String reminderType;
    private LocalDateTime triggerAt;
    private String message;
}
