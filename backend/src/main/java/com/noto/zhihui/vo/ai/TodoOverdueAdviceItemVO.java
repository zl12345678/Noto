package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoOverdueAdviceItemVO {

    private Long todoId;
    private String title;
    /** reschedule | breakdown | archive | complete */
    private String action;
    private String reason;
    private LocalDateTime suggestedDueAt;
}
