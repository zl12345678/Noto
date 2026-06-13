package com.noto.zhihui.dto.note;

import lombok.Data;

@Data
public class NoteQueryRequest {
    private String keyword;
    private Integer status;
    private Boolean isFavorite;
}
