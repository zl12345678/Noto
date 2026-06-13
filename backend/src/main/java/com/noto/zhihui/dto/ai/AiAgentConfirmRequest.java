package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AiAgentConfirmRequest {

    @NotEmpty
    private List<String> stepIds;
}
