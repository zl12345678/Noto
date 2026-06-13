package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDailySuggestionVO {

    private Long todoId;
    private String title;
    private String reason;
    /** start=从队列开做, continue=继续推进, focus=今日重点 */
    private String action;
}
