package com.noto.zhihui.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class AdminAuditLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private Long workspaceId;
    private String actionType;
    private String resourceType;
    private Long resourceId;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> detail;
    private LocalDateTime createdAt;
}
