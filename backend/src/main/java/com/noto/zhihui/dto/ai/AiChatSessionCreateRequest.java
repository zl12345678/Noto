package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiChatSessionCreateRequest {

    @NotNull
    private Long workspaceId;

    private String scope = "workspace";

    private Long targetId;

    private String title;
}
