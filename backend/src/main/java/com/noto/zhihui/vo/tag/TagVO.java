package com.noto.zhihui.vo.tag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TagVO {

    private Long id;
    private Long workspaceId;
    private String name;
    private String color;
}
