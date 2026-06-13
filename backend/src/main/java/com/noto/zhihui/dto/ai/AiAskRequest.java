package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiAskRequest {

    private Long workspaceId;

    @NotBlank
    private String question;

    private String scope = "workspace";

    private Long targetId;

    /** 持久化会话 ID；传入则在问答完成后写入 ai_chat_message */
    private Long sessionId;

    /** 可选：近期对话上下文，用于理解指代与追问 */
    private String recentContext;
}
