package com.noto.zhihui.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceUpdateRequest {

    @NotBlank
    private String name;

    private String description;
}
