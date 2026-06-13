package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.constants.AiTaskStatus;
import com.noto.zhihui.common.constants.AiTaskType;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.entity.AiTaskEntity;
import com.noto.zhihui.mapper.AiTaskMapper;
import com.noto.zhihui.service.AiDigestService;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiDailySuggestionsVO;
import com.noto.zhihui.vo.ai.AiDigestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AiDigestServiceImpl implements AiDigestService {

    private static final Logger log = LoggerFactory.getLogger(AiDigestServiceImpl.class);

    private final AiTaskMapper aiTaskMapper;
    private final AiService aiService;
    private final UserSettingService userSettingService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AiDigestServiceImpl(
            AiTaskMapper aiTaskMapper,
            AiService aiService,
            UserSettingService userSettingService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.aiService = aiService;
        this.userSettingService = userSettingService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiDigestVO getTodayDigest(Long userId) {
        AiTaskEntity task = findTodayTask(userId);
        return task == null ? null : toDigestVO(task);
    }

    @Override
    @Transactional
    public AiDigestVO generateDigest(Long userId, Long workspaceId) {
        AiTaskEntity existing = findTodayTask(userId);
        if (existing != null) {
            return toDigestVO(existing);
        }
        return toDigestVO(createDigestTask(userId, workspaceId));
    }

    @Override
    public void runScheduledDigests(int hour) {
        List<Long> userIds = userSettingService.listUserIdsForDailyDigestHour(hour);
        for (Long userId : userIds) {
            try {
                if (findTodayTask(userId) != null) {
                    continue;
                }
                createDigestTask(userId, null);
            } catch (Exception ex) {
                log.warn("Daily digest failed for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    private AiTaskEntity createDigestTask(Long userId, Long workspaceId) {
        long started = System.currentTimeMillis();
        AiTaskEntity task = new AiTaskEntity();
        task.setUserId(userId);
        task.setWorkspaceId(workspaceId);
        task.setTaskType(AiTaskType.DAILY_DIGEST);
        task.setStatus(AiTaskStatus.PROCESSING);
        task.setInputContent(LocalDate.now().toString());
        task.setRetryCount(0);
        aiTaskMapper.insert(task);

        try {
            AiDailySuggestionsVO suggestions = aiService.suggestDailyActions(userId, workspaceId);
            AiDailyReviewVO review = null;
            if (LocalTime.now().getHour() >= 18) {
                review = aiService.reviewDailyProgress(userId, workspaceId);
            }
            DigestPayload payload = new DigestPayload();
            payload.setDigestDate(LocalDate.now().toString());
            payload.setSuggestions(suggestions);
            payload.setReview(review);
            task.setOutputContent(objectMapper.writeValueAsString(payload));
            task.setStatus(AiTaskStatus.SUCCESS);
            aiTaskMapper.updateById(task);

            auditLogService.logAiCall(
                    userId,
                    workspaceId,
                    "ai.daily_digest",
                    "ai_task",
                    task.getId(),
                    Map.of("durationMs", System.currentTimeMillis() - started)
            );
            return task;
        } catch (Exception ex) {
            task.setStatus(AiTaskStatus.FAILED);
            task.setErrorMessage(trimError(ex.getMessage()));
            aiTaskMapper.updateById(task);
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "生成每日 digest 失败：" + ex.getMessage());
        }
    }

    private AiTaskEntity findTodayTask(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getUserId, userId)
                .eq(AiTaskEntity::getTaskType, AiTaskType.DAILY_DIGEST)
                .ge(AiTaskEntity::getCreatedAt, start)
                .lt(AiTaskEntity::getCreatedAt, end)
                .orderByDesc(AiTaskEntity::getCreatedAt)
                .last("LIMIT 1"));
    }

    private AiDigestVO toDigestVO(AiTaskEntity task) {
        AiDigestVO vo = new AiDigestVO();
        vo.setTaskId(task.getId());
        vo.setGeneratedAt(task.getCreatedAt());
        if (!StringUtils.hasText(task.getOutputContent())) {
            vo.setDigestDate(LocalDate.now().toString());
            return vo;
        }
        try {
            DigestPayload payload = objectMapper.readValue(task.getOutputContent(), DigestPayload.class);
            vo.setDigestDate(payload.getDigestDate());
            vo.setSuggestions(payload.getSuggestions());
            vo.setReview(payload.getReview());
        } catch (Exception ex) {
            vo.setDigestDate(task.getInputContent());
        }
        return vo;
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static final class DigestPayload {
        private String digestDate;
        private AiDailySuggestionsVO suggestions;
        private AiDailyReviewVO review;

        public String getDigestDate() {
            return digestDate;
        }

        public void setDigestDate(String digestDate) {
            this.digestDate = digestDate;
        }

        public AiDailySuggestionsVO getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(AiDailySuggestionsVO suggestions) {
            this.suggestions = suggestions;
        }

        public AiDailyReviewVO getReview() {
            return review;
        }

        public void setReview(AiDailyReviewVO review) {
            this.review = review;
        }
    }
}
