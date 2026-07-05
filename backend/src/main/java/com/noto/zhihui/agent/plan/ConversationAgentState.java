package com.noto.zhihui.agent.plan;

import lombok.Data;

@Data
public class ConversationAgentState {

    private String status = "idle";
    private String activePlanId;

    public static ConversationAgentState fromPendingPlan(PendingPlanState pendingPlan) {
        ConversationAgentState state = new ConversationAgentState();
        if (pendingPlan != null && pendingPlan.hasPendingSteps()) {
            state.setStatus("pending_confirm");
            state.setActivePlanId(pendingPlan.getPlanId());
        }
        return state;
    }
}
