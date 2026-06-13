package com.noto.zhihui.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteClipImportRequest {

    @NotNull(message = "知识库不能为空")
    private Long workspaceId;

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    /** 剪藏来源 URL */
    private String sourceUrl;

    /** html | markdown | plain */
    private String contentType = "plain";

    private Long folderId;

    /** 导入后是否用 AI 结构化为会议纪要格式 */
    private Boolean structureWithAi = false;
}
