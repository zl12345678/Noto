package com.noto.zhihui.agent.plan;

import com.noto.zhihui.vo.ai.AiAgentStepVO;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class PendingPlanState {

    private String planId;
    private String status;
    private List<PendingPlanStep> steps = new ArrayList<>();

    public static PendingPlanState fromSteps(String planId, List<AiAgentStepVO> agentSteps) {
        PendingPlanState state = new PendingPlanState();
        state.setPlanId(planId);
        state.setStatus("idle");
        if (agentSteps == null) {
            return state;
        }
        for (AiAgentStepVO step : agentSteps) {
            if (!"pending_confirm".equals(step.getStatus()) || step.getActionPayload() == null) {
                continue;
            }
            PendingPlanStep pending = new PendingPlanStep();
            pending.setStepId(step.getId());
            pending.setTool(step.getTool());
            pending.setArgs(step.getActionPayload());
            pending.setStatus(step.getStatus());
            state.getSteps().add(pending);
        }
        if (!state.getSteps().isEmpty()) {
            state.setStatus("pending_confirm");
        }
        if (!StringUtils.hasText(state.getPlanId())) {
            state.setPlanId("pending-plan");
        }
        return state;
    }

    public boolean hasPendingSteps() {
        return steps != null && !steps.isEmpty();
    }
}
