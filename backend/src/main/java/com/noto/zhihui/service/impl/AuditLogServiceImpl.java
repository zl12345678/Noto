package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.entity.AuditLogEntity;
import com.noto.zhihui.mapper.AuditLogMapper;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.vo.ai.AiObservabilitySummaryVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final int DETAIL_MAX = 4000;
    private static final int SUMMARY_SAMPLE_SIZE = 100;
    private static final int RECENT_ACTION_SIZE = 8;

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

    @Override
    public AiObservabilitySummaryVO aiObservabilitySummary(Long userId) {
        if (userId == null) {
            return new AiObservabilitySummaryVO(0, 0, null, List.of());
        }
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getUserId, userId)
                .likeRight(AuditLogEntity::getActionType, "ai.")
                .orderByDesc(AuditLogEntity::getCreatedAt);

        Long total = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getUserId, userId)
                .likeRight(AuditLogEntity::getActionType, "ai."));
        List<AuditLogEntity> records = auditLogMapper.selectList(wrapper.last("LIMIT " + SUMMARY_SAMPLE_SIZE));

        long successCount = records.stream().filter(this::readSuccess).count();
        double successRate = records.isEmpty() ? 0 : roundRate((successCount * 100.0) / records.size());
        List<Long> latencies = records.stream()
                .map(this::readLatencyMs)
                .filter(Objects::nonNull)
                .toList();
        Long averageLatency = latencies.isEmpty()
                ? null
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        List<AiObservabilitySummaryVO.AiObservabilityActionVO> recent = records.stream()
                .limit(RECENT_ACTION_SIZE)
                .map(entity -> new AiObservabilitySummaryVO.AiObservabilityActionVO(
                        entity.getActionType(),
                        entity.getResourceType(),
                        readSuccess(entity),
                        readLatencyMs(entity),
                        entity.getCreatedAt()
                ))
                .toList();
        return new AiObservabilitySummaryVO(total == null ? 0 : total, successRate, averageLatency, recent);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDetail(AuditLogEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getDetail())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(entity.getDetail(), Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean readSuccess(AuditLogEntity entity) {
        Object value = readDetail(entity).get("success");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return true;
    }

    private Long readLatencyMs(AuditLogEntity entity) {
        Object value = readDetail(entity).get("durationMs");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double roundRate(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
