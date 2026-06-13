package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_chat_session")
public class AiChatSessionEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("user_id")
    private Long userId;

    @TableField("session_type")
    private String sessionType;

    private String title;

    @TableField("latest_message_at")
    private LocalDateTime latestMessageAt;
}
