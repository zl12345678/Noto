package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class AiChatMessageVO {

    private Long id;
    private String role;
    private String content;
    private List<AiReferenceVO> references;
    private LocalDateTime createdAt;
}
