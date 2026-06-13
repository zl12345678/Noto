package com.noto.zhihui.vo.note;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ExtractedTodoSuggestionVO {

    private String title;
    private boolean completed;
    private int priority;
    private String horizon;
    private LocalDateTime dueAt;
    /** 同文档下是否已存在同名待办 */
    private boolean duplicate;
}
