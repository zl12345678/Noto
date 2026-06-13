package com.noto.zhihui.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class NoteSynthesizeVO {

    private String template;
    private String title;
    private String content;
    private List<NoteSynthesizeSourceVO> sources;
    /** 保存为新笔记后的 ID */
    private Long createdNoteId;
}
