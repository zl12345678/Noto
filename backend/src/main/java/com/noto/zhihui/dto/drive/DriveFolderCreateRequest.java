package com.noto.zhihui.dto.drive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriveFolderCreateRequest {

    @NotNull
    private Long workspaceId;

    private Long parentId;

    @NotBlank
    private String name;
}
