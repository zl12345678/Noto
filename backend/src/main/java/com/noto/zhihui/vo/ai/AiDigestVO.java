package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiDigestVO {

    private Long taskId;
    private String digestDate;
    private LocalDateTime generatedAt;
    private AiDailySuggestionsVO suggestions;
    private AiDailyReviewVO review;
}
