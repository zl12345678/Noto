package com.noto.zhihui.dto.folder;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FolderTreeMoveRequest {

    private Long parentId;

    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;
}
