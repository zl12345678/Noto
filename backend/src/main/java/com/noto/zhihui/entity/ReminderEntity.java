package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("reminder")
public class ReminderEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("todo_id")
    private Long todoId;

    @TableField("reminder_type")
    private String reminderType;

    @TableField("trigger_at")
    private LocalDateTime triggerAt;

    private String message;
    private Integer status;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("created_by")
    private Long createdBy;
}
