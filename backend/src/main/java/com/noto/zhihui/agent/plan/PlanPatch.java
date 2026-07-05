package com.noto.zhihui.agent.plan;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class PlanPatch {

    private String type;
    private String question;
    private String targetTool;
    private String targetStepId;
    private Map<String, Object> updates = new HashMap<>();

    public boolean isClarify() {
        return "clarify".equals(type);
    }

    public boolean isModifyPendingPlan() {
        return "modify_pending_plan".equals(type);
    }
}
