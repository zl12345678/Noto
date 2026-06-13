package com.noto.zhihui.dto.note;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteStatusRequest {

    @NotNull
    private Integer status;
}
