package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiAskVO {

    private String answer;
    private List<AiReferenceVO> references;
    /** 资料不足时建议补充的笔记类型 */
    private List<String> knowledgeGaps;
}
