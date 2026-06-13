package com.noto.zhihui.vo.note;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NoteImportVO {

    private Long noteId;
    private String noteTitle;
    private boolean structured;
}
