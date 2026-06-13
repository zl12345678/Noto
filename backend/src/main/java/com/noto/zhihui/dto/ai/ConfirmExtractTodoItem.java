package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfirmExtractTodoItem {

    @NotBlank
    @Size(max = 500)
    private String title;

    private boolean completed;

    private Integer priority;

    private String horizon;

    private LocalDateTime dueAt;
}
