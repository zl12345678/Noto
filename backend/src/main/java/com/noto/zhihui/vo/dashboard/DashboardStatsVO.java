package com.noto.zhihui.vo.dashboard;

import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.todo.TodoVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DashboardStatsVO {

    private long totalNotes;
    private long favoriteNotes;
    private long archivedNotes;
    private long totalTodos;
    private long pendingTodos;
    private long completedTodos;
    private long overdueTodos;
    private List<NoteVO> recentNotes;
    private List<TodoVO> actionTodos;
    private List<TodoVO> parallelTodos;
    private List<TodoVO> longTermTodos;
    private long parallelActiveCount;
    private long longTermActiveCount;
    private long todayCompletedTodos;
    /** 是否适合展示新手引导（无用户文档、无待办活动） */
    private boolean onboardingEligible;
}
