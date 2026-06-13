package com.noto.zhihui.vo.drive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriveFolderVO {

    private Long id;
    private Long workspaceId;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Long fileCount;
}
