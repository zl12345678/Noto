package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("note_attachment_link")
public class NoteAttachmentLinkEntity extends BaseEntity {

    @TableField("attachment_id")
    private Long attachmentId;

    @TableField("note_id")
    private Long noteId;
}
