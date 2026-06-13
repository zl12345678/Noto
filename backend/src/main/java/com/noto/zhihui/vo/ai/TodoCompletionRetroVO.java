package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TodoCompletionRetroVO {

    private String line;
    private Long noteId;
    private String noteTitle;
    private String todoTitle;
    private boolean appended;
}
