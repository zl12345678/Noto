package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.vo.admin.AdminAuditLogVO;
import com.noto.zhihui.vo.admin.AdminAuditSummaryVO;
import com.noto.zhihui.vo.ai.AiObservabilitySummaryVO;

import java.time.LocalDateTime;
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

    void logOperation(
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId,
            String ipAddress,
            String userAgent,
            Map<String, Object> detail
    );

    AiObservabilitySummaryVO aiObservabilitySummary(Long userId);

    Page<AdminAuditLogVO> listAdminLogs(
            long page,
            long size,
            Long userId,
            String actionType,
            String ipAddress,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    AdminAuditSummaryVO adminSummary(LocalDateTime startAt, LocalDateTime endAt);
}
