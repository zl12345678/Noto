package com.noto.zhihui.dto.ai;

import lombok.Data;

@Data
public class TodoCompletionRetroRequest {

    /** 复盘文案；为空时由 AI 生成 */
    private String line;

    /** 是否追加写入关联笔记 */
    private Boolean append = false;

    /** reminder_due | completed */
    private String scenario;
}
