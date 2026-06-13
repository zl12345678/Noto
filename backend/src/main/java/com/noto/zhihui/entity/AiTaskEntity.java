package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_task")
public class AiTaskEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("user_id")
    private Long userId;

    @TableField("task_type")
    private String taskType;

    @TableField("target_id")
    private Long targetId;

    private Integer status;

    @TableField("input_content")
    private String inputContent;

    @TableField("output_content")
    private String outputContent;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("error_message")
    private String errorMessage;
}
