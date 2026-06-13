package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiAgentPlanRequest {

    @NotNull
    private Long workspaceId;

    @NotBlank
    private String instruction;

    /** 可选：会话 ID，用于加载历史上下文 */
    private Long sessionId;

    /** 可选：近期对话上下文 */
    private String recentContext;
}
