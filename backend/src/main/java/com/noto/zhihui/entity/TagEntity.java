package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tag")
public class TagEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    private String name;
    private String color;
}
