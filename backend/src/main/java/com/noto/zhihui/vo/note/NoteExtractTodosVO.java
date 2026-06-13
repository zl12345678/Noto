package com.noto.zhihui.vo.note;

import com.noto.zhihui.vo.todo.TodoVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NoteExtractTodosVO {

    private int createdCount;
    private int skippedCount;
    private List<TodoVO> todos;
}
