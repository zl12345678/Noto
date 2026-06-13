package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("todo_item")
public class TodoItemEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("note_id")
    private Long noteId;

    private String title;
    private String description;
    private Integer priority;
    private Integer status;

    @TableField("due_at")
    private LocalDateTime dueAt;

    /** action=短期行动, long_term=长期待办 */
    private String horizon;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_by")
    private Long createdBy;
}
