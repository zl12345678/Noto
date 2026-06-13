package com.noto.zhihui.vo.note;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NoteExtractTodosPreviewVO {

    private Long noteId;
    private String noteTitle;
    private List<ExtractedTodoSuggestionVO> suggestions;
}
