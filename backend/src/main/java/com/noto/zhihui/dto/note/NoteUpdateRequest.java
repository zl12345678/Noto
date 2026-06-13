package com.noto.zhihui.dto.note;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NoteUpdateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;

    private String contentType = "markdown";
    private Long folderId;
    private Long parentId;
    private Integer status;
    private Boolean isFavorite;
    private String summary;
    private List<Long> tagIds;
    private Boolean autoSummary;
}
