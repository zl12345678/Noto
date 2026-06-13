package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDailySuggestionsVO {

    private String summary;
    private List<AiDailySuggestionVO> suggestions;
}
