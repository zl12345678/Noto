package com.noto.zhihui.vo.share;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedFileItemVO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
}
