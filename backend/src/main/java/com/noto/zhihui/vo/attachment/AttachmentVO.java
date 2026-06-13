package com.noto.zhihui.vo.attachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentVO {

    private Long id;
    private Long workspaceId;
    private Long noteId;
    private Long folderId;
    private String folderName;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private String storageKey;
    private String createdAt;
    private List<AttachmentLinkedNoteVO> linkedNotes;
}
