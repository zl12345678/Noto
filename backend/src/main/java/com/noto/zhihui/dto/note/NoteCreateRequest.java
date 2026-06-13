package com.noto.zhihui.dto.note;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NoteCreateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    private String contentType = "markdown";
    private Long workspaceId;
    private Long folderId;
    private Long parentId;
    private List<Long> tagIds;
}
