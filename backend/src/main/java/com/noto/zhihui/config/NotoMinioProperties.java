package com.noto.zhihui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "noto.minio")
public class NotoMinioProperties {

    private boolean enabled = false;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "noto";
    private long maxFileSizeBytes = 100L * 1024 * 1024;

    public String formatMaxFileSize() {
        if (maxFileSizeBytes >= 1024 * 1024 && maxFileSizeBytes % (1024 * 1024) == 0) {
            return (maxFileSizeBytes / (1024 * 1024)) + "MB";
        }
        if (maxFileSizeBytes >= 1024 && maxFileSizeBytes % 1024 == 0) {
            return (maxFileSizeBytes / 1024) + "KB";
        }
        return maxFileSizeBytes + "B";
    }

    public String fileTooLargeMessage() {
        return "文件大小超出限制（单文件最大 " + formatMaxFileSize() + "）";
    }
}
