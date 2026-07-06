package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class AiAgentStepUpdateRequest {

    @NotEmpty
    private Map<String, Object> actionPayload;
}
