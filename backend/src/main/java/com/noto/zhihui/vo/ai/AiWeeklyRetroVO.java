package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiWeeklyRetroVO {

    private Long taskId;
    private String weekStart;
    private Long noteId;
    private String noteTitle;
    private LocalDateTime generatedAt;
}
