package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDailyReviewVO {

    private String summary;
    private List<String> highlights;
    private List<String> blockers;
    private List<String> tomorrowFocus;
    private long completedCount;
}
