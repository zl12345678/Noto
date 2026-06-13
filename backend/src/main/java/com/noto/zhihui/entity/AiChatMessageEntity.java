package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    private String role;

    private String content;

    @TableField("token_usage")
    private Integer tokenUsage;

    @TableField("model_name")
    private String modelName;

    @TableField("referenced_note_ids")
    private String referencedNoteIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;
}
