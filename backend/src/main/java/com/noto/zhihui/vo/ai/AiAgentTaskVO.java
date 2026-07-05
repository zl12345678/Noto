package com.noto.zhihui.vo.ai;

import lombok.Data;

import com.noto.zhihui.agent.plan.ConversationAgentState;
import com.noto.zhihui.agent.plan.PendingPlanState;

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
    private PendingPlanState pendingPlan;
    private ConversationAgentState agentState;
    private Boolean autoExecuted;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
