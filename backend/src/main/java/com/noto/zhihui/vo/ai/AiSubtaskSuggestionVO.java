package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSubtaskSuggestionVO {

    private String title;
    private Integer priority;
    private String horizon;
    private LocalDateTime dueAt;
}
