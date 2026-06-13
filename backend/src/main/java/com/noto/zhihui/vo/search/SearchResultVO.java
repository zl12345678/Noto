package com.noto.zhihui.vo.search;

import com.noto.zhihui.vo.tag.TagVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class SearchResultVO {

    private Long noteId;
    private String title;
    private String snippet;
    private String highlight;
    private Long workspaceId;
    private String workspaceName;
    private Long folderId;
    private LocalDateTime lastEditedAt;
    private List<TagVO> tags;
    /** 正文内首个匹配位置，供前端跳转定位 */
    private Integer offsetStart;
    private Integer offsetEnd;
}
