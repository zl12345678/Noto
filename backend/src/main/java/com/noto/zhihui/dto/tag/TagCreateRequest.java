package com.noto.zhihui.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TagCreateRequest {

    @NotNull
    private Long workspaceId;

    @NotBlank
    private String name;

    private String color;
}
