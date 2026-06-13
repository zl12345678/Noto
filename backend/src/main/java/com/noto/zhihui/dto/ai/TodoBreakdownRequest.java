package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoBreakdownRequest {

    @NotBlank
    private String title;

    private String description;

    private String horizon = "action";
}
