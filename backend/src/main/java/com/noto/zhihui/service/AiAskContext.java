package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.AiReferenceVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiAskContext {

    private String prompt;
    private List<AiReferenceVO> references;
    /** 检索侧推断的知识缺口（与模型输出合并） */
    private List<String> retrievalGaps;
}
