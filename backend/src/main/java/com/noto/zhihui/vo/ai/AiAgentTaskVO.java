package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiAgentTaskVO {

    private Long id;
    private Long workspaceId;
    private String taskType;
    private Integer status;
    private String instruction;
    private String assistantReply;
    private List<AiAgentStepVO> steps;
    private Boolean autoExecuted;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
