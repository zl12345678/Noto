package com.noto.zhihui.agent.plan;

import lombok.Data;

import java.util.Map;

@Data
public class PendingPlanStep {

    private String stepId;
    private String tool;
    private Map<String, Object> args;
    private String status;
}
