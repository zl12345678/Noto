package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("note")
public class NoteEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("folder_id")
    private Long folderId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("sort_order")
    private Integer sortOrder;

    private String title;
    private String content;

    @TableField("content_type")
    private String contentType;

    private String summary;
    private Integer status;

    @TableField("is_favorite")
    private Boolean isFavorite;

    @TableField("last_edited_at")
    private LocalDateTime lastEditedAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField(exist = false)
    private String createdByName;
}
