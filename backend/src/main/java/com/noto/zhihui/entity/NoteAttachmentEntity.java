package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("note_attachment")
public class NoteAttachmentEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("uploaded_by")
    private Long uploadedBy;

    @TableField("note_id")
    private Long noteId;

    @TableField("folder_id")
    private Long folderId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_url")
    private String fileUrl;

    @TableField("storage_key")
    private String storageKey;
}
