package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoBreakdownVO {

    private String sourceTitle;
    private List<AiSubtaskSuggestionVO> subtasks;
}
