package com.noto.zhihui.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.entity.AuditLogEntity;
import com.noto.zhihui.mapper.AuditLogMapper;
import com.noto.zhihui.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final int DETAIL_MAX = 4000;

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void logAiCall(
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId,
            Map<String, Object> detail
    ) {
        if (userId == null || !StringUtils.hasText(actionType)) {
            return;
        }
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setUserId(userId);
            entity.setWorkspaceId(workspaceId);
            entity.setActionType(actionType);
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setDetail(serializeDetail(detail));
            auditLogMapper.insert(entity);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    private String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(detail);
            if (json.length() <= DETAIL_MAX) {
                return json;
            }
            return json.substring(0, DETAIL_MAX) + "...";
        } catch (Exception ex) {
            return "{\"error\":\"detail serialize failed\"}";
        }
    }
}
