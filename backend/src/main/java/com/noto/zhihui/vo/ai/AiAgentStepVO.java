package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.util.Map;

@Data
public class AiAgentStepVO {

    private String id;
    private String tool;
    private String status;
    private boolean requiresConfirm;
    private Map<String, Object> input;
    private String output;
    private Map<String, Object> actionPayload;
    private Map<String, Object> result;
}
