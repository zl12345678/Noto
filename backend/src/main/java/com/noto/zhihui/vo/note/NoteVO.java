package com.noto.zhihui.vo.note;

import com.noto.zhihui.vo.tag.TagVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class NoteVO {

    private Long id;
    private Long workspaceId;
    private Long folderId;
    private Long parentId;
    private Integer sortOrder;
    private String title;
    private String content;
    private String excerpt;
    private String contentType;
    private String summary;
    private Integer status;
    private Boolean isFavorite;
    private LocalDateTime lastEditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TagVO> tags;
}
