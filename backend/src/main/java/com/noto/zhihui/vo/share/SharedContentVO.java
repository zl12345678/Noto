package com.noto.zhihui.vo.share;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedContentVO {

    private String resourceType;
    private String title;
    private String content;
    private String contentType;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private Boolean passwordRequired;
    private Long viewCount;
    private String expiresAt;
    private String sharedAt;
    private List<SharedFileItemVO> files;
}
