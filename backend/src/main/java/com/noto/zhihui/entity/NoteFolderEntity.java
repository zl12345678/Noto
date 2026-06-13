package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("note_folder")
public class NoteFolderEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("parent_id")
    private Long parentId;

    private String name;

    @TableField("sort_order")
    private Integer sortOrder;
}
