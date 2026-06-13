package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.AiReferenceVO;

import java.util.List;

public interface AiStreamSink {

    void sendReferences(List<AiReferenceVO> references);

    void sendToken(String token);

    default void sendKnowledgeGaps(java.util.List<String> gaps) {
    }

    void complete();

    void fail(String message);
}
