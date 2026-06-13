package com.noto.zhihui.dto.note;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteTreeMoveRequest {

    private Long folderId;
    private Long parentId;

    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;
}
