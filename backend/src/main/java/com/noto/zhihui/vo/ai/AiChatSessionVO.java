package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AiChatSessionVO {

    private Long id;
    private Long workspaceId;
    private String scope;
    private Long targetId;
    private String title;
    private LocalDateTime latestMessageAt;
    private LocalDateTime createdAt;
}
