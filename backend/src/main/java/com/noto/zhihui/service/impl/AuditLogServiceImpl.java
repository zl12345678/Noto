package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.entity.AuditLogEntity;
import com.noto.zhihui.entity.UserEntity;
import com.noto.zhihui.mapper.AuditLogMapper;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.UserService;
import com.noto.zhihui.vo.admin.AdminAuditLogVO;
import com.noto.zhihui.vo.admin.AdminAuditSummaryVO;
import com.noto.zhihui.vo.ai.AiObservabilitySummaryVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final int DETAIL_MAX = 4000;
    private static final int SUMMARY_SAMPLE_SIZE = 100;
    private static final int RECENT_ACTION_SIZE = 8;

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    public AuditLogServiceImpl(
            AuditLogMapper auditLogMapper,
            ObjectMapper objectMapper,
            UserService userService,
            JdbcTemplate jdbcTemplate
    ) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
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
        logOperation(userId, workspaceId, actionType, resourceType, resourceId, null, null, detail);
    }

    @Override
    public void logOperation(
            Long userId,
            Long workspaceId,
            String actionType,
            String resourceType,
            Long resourceId,
            String ipAddress,
            String userAgent,
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
            entity.setIpAddress(trim(ipAddress, 64));
            entity.setUserAgent(trim(userAgent, 512));
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

    @Override
    public Page<AdminAuditLogVO> listAdminLogs(
            long page,
            long size,
            Long userId,
            String actionType,
            String ipAddress,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<AuditLogEntity> wrapper = buildAdminWrapper(userId, actionType, ipAddress, startAt, endAt)
                .orderByDesc(AuditLogEntity::getCreatedAt);
        Page<AuditLogEntity> entityPage = auditLogMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        Map<Long, UserEntity> users = loadUsers(entityPage.getRecords());
        Page<AdminAuditLogVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(entity -> toAdminVO(entity, users.get(entity.getUserId())))
                .toList());
        return voPage;
    }

    @Override
    public AdminAuditSummaryVO adminSummary(LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new AdminAuditSummaryVO(
                count("COUNT(*)", startAt, endAt),
                count("COUNT(*)", todayStart, null),
                count("COUNT(DISTINCT user_id)", startAt, endAt),
                count("COUNT(DISTINCT user_id)", todayStart, null),
                count("COUNT(DISTINCT ip_address)", startAt, endAt),
                count("COUNT(DISTINCT ip_address)", todayStart, null)
        );
    }

    private LambdaQueryWrapper<AuditLogEntity> buildAdminWrapper(
            Long userId,
            String actionType,
            String ipAddress,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(AuditLogEntity::getUserId, userId);
        }
        if (StringUtils.hasText(actionType)) {
            wrapper.like(AuditLogEntity::getActionType, actionType.trim());
        }
        if (StringUtils.hasText(ipAddress)) {
            wrapper.like(AuditLogEntity::getIpAddress, ipAddress.trim());
        }
        if (startAt != null) {
            wrapper.ge(AuditLogEntity::getCreatedAt, startAt);
        }
        if (endAt != null) {
            wrapper.le(AuditLogEntity::getCreatedAt, endAt);
        }
        return wrapper;
    }

    private Map<Long, UserEntity> loadUsers(List<AuditLogEntity> records) {
        Set<Long> userIds = records.stream()
                .map(AuditLogEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, item -> item, (left, right) -> left));
    }

    private AdminAuditLogVO toAdminVO(AuditLogEntity entity, UserEntity user) {
        return new AdminAuditLogVO(
                entity.getId(),
                entity.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getNickname(),
                entity.getWorkspaceId(),
                entity.getActionType(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                readDetail(entity),
                entity.getCreatedAt()
        );
    }

    private long count(String expression, LocalDateTime startAt, LocalDateTime endAt) {
        StringBuilder sql = new StringBuilder("SELECT ").append(expression).append(" FROM audit_log WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        if (startAt != null) {
            sql.append(" AND created_at >= ?");
            params.put("startAt", startAt);
        }
        if (endAt != null) {
            sql.append(" AND created_at <= ?");
            params.put("endAt", endAt);
        }
        List<Object> values = new java.util.ArrayList<>();
        if (params.containsKey("startAt")) {
            values.add(params.get("startAt"));
        }
        if (params.containsKey("endAt")) {
            values.add(params.get("endAt"));
        }
        Long value = jdbcTemplate.queryForObject(sql.toString(), Long.class, values.toArray());
        return value == null ? 0 : value;
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

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
