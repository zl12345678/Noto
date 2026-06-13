package com.noto.zhihui.vo.reminder;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ReminderVO {

    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private Long todoId;
    private String todoTitle;
    private String reminderType;
    private LocalDateTime triggerAt;
    private String message;
    private Integer status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 关联待办来源笔记 */
    private Long noteId;
    private String noteTitle;
    /** 触发提醒时附带的笔记摘要/摘录 */
    private String noteContext;
}
