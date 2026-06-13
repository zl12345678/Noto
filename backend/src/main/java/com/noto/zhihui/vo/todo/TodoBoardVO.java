package com.noto.zhihui.vo.todo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TodoBoardVO {

    private List<TodoVO> actionTodos;
    private List<TodoVO> parallelTodos;
    private List<TodoVO> longTermTodos;
    private long longTermActiveCount;
    private long parallelActiveCount;
    private long todayCompletedTodos;
}
