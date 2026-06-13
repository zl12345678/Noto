package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReferenceVO {

    private Long noteId;
    private String noteTitle;
    private String snippet;
    /** 原文中的起始偏移（字符），无法定位时为 null */
    private Integer offsetStart;
    /** 原文中的结束偏移（字符），无法定位时为 null */
    private Integer offsetEnd;
    /** 用于编辑器高亮的关键词 */
    private String highlightKeyword;
    /** 检索相关度 0-100，越高越靠前 */
    private Integer relevanceScore;

    public AiReferenceVO(Long noteId, String noteTitle, String snippet) {
        this(noteId, noteTitle, snippet, null, null, null, null);
    }
}
