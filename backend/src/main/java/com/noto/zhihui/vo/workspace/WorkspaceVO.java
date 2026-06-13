package com.noto.zhihui.vo.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class WorkspaceVO {

    private Long id;
    private String name;
    private Integer type;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private Long homeNoteId;
}
