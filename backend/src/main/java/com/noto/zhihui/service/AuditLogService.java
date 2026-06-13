package com.noto.zhihui.service;

import java.util.Map;

public interface AuditLogService {

    void logAiCall(
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId,
            Map<String, Object> detail
    );
}
