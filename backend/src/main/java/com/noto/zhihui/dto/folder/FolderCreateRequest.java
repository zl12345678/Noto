package com.noto.zhihui.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FolderCreateRequest {

    @NotNull
    private Long workspaceId;

    private Long parentId;

    @NotBlank
    private String name;
}
