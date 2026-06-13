package com.noto.zhihui.dto.drive;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DriveBatchDownloadRequest {

    @NotEmpty(message = "请选择要下载的文件")
    @Size(max = 50, message = "单次最多下载 50 个文件")
    private List<String> ids;
}
