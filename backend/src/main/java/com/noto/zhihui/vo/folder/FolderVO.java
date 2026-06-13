package com.noto.zhihui.vo.folder;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FolderVO {

    private Long id;
    private Long workspaceId;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
