package com.noto.zhihui.dto.folder;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderUpdateRequest {

    @NotBlank
    private String name;

    private Long parentId;

    private Integer sortOrder;
}
