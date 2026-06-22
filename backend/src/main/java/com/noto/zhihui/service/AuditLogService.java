package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.AiObservabilitySummaryVO;

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

    AiObservabilitySummaryVO aiObservabilitySummary(Long userId);
}
