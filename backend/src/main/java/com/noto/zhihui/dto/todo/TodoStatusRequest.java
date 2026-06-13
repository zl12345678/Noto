package com.noto.zhihui.dto.todo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TodoStatusRequest {

    @NotNull
    private Integer status;
}
