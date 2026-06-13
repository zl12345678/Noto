package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("workspace")
public class WorkspaceEntity extends BaseEntity {

    @TableField("owner_user_id")
    private Long ownerUserId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    private String name;
    private Integer type;
    private String description;
    private Integer status;

    @TableField("home_note_id")
    private Long homeNoteId;
}
