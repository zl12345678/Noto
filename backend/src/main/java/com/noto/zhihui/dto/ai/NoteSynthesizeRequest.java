package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class NoteSynthesizeRequest {

    @NotNull(message = "知识库不能为空")
    private Long workspaceId;

    /** 主题 / 关键词，用于检索相关文档 */
    @NotBlank(message = "主题不能为空")
    private String topic;

    /** decision | status | weekly | retro */
    @NotBlank(message = "模板不能为空")
    private String template;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    /** 指定文档 ID（可选，最多 8 篇） */
    private List<Long> noteIds;

    /** 是否保存为新笔记，默认 true */
    private Boolean saveAsNote = true;

    private Long folderId;
}
