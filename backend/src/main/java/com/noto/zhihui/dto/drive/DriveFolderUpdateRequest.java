package com.noto.zhihui.dto.drive;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DriveFolderUpdateRequest {

    @NotBlank
    private String name;
}
