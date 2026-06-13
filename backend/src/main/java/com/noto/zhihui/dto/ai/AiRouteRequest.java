package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRouteRequest {

    @NotBlank
    private String message;

    /** 可选：会话 ID，用于加载历史上下文 */
    private Long sessionId;

    /** 可选：前端传入的近期对话摘要（优先于 session 加载） */
    private String recentContext;
}
