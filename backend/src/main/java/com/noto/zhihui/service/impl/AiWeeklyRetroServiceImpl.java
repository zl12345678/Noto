package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.constants.AiTaskStatus;
import com.noto.zhihui.common.constants.AiTaskType;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.NoteSynthesizeRequest;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.entity.AiTaskEntity;
import com.noto.zhihui.mapper.AiTaskMapper;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.AiWeeklyRetroService;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.ai.AiDailyReviewVO;
import com.noto.zhihui.vo.ai.AiWeeklyRetroVO;
import com.noto.zhihui.vo.ai.NoteSynthesizeVO;
import com.noto.zhihui.vo.note.NoteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class AiWeeklyRetroServiceImpl implements AiWeeklyRetroService {

    private static final Logger log = LoggerFactory.getLogger(AiWeeklyRetroServiceImpl.class);
    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AiTaskMapper aiTaskMapper;
    private final AiService aiService;
    private final NoteService noteService;
    private final WorkspaceService workspaceService;
    private final UserSettingService userSettingService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AiWeeklyRetroServiceImpl(
            AiTaskMapper aiTaskMapper,
            AiService aiService,
            NoteService noteService,
            WorkspaceService workspaceService,
            UserSettingService userSettingService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.aiService = aiService;
        this.noteService = noteService;
        this.workspaceService = workspaceService;
        this.userSettingService = userSettingService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiWeeklyRetroVO getThisWeekRetro(Long userId) {
        AiTaskEntity task = findThisWeekTask(userId);
        return task == null ? null : toRetroVO(task);
    }

    @Override
    @Transactional
    public AiWeeklyRetroVO generateWeeklyRetro(Long userId, Long workspaceId) {
        AiTaskEntity existing = findThisWeekTask(userId);
        if (existing != null) {
            return toRetroVO(existing);
        }
        return toRetroVO(createWeeklyRetroTask(userId, workspaceId));
    }

    @Override
    public void runScheduledWeeklyRetros(int hour) {
        if (LocalDate.now().getDayOfWeek() != DayOfWeek.FRIDAY) {
            return;
        }
        List<Long> userIds = userSettingService.listUserIdsForWeeklyRetroHour(hour);
        for (Long userId : userIds) {
            try {
                if (findThisWeekTask(userId) != null) {
                    continue;
                }
                createWeeklyRetroTask(userId, null);
            } catch (Exception ex) {
                log.warn("Weekly retro failed for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    private AiTaskEntity createWeeklyRetroTask(Long userId, Long workspaceId) {
        long started = System.currentTimeMillis();
        Long wsId = resolveWorkspaceId(userId, workspaceId);
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate today = LocalDate.now();

        AiTaskEntity task = new AiTaskEntity();
        task.setUserId(userId);
        task.setWorkspaceId(wsId);
        task.setTaskType(AiTaskType.WEEKLY_RETRO);
        task.setStatus(AiTaskStatus.PROCESSING);
        task.setInputContent(weekStart.toString());
        task.setRetryCount(0);
        aiTaskMapper.insert(task);

        try {
            RetroResult retro = synthesizeOrFallback(userId, wsId, weekStart, today);
            RetroPayload payload = new RetroPayload();
            payload.setWeekStart(weekStart.toString());
            payload.setNoteId(retro.noteId());
            payload.setNoteTitle(retro.noteTitle());
            task.setOutputContent(objectMapper.writeValueAsString(payload));
            task.setStatus(AiTaskStatus.SUCCESS);
            aiTaskMapper.updateById(task);

            auditLogService.logAiCall(
                    userId,
                    wsId,
                    "ai.weekly_retro",
                    "ai_task",
                    task.getId(),
                    Map.of("noteId", retro.noteId(), "durationMs", System.currentTimeMillis() - started)
            );
            return task;
        } catch (Exception ex) {
            task.setStatus(AiTaskStatus.FAILED);
            task.setErrorMessage(trimError(ex.getMessage()));
            aiTaskMapper.updateById(task);
            throw new BizException(ErrorCode.AI_ERROR.getCode(), "生成每周复盘失败：" + ex.getMessage());
        }
    }

    private RetroResult synthesizeOrFallback(Long userId, Long workspaceId, LocalDate weekStart, LocalDate today) {
        NoteSynthesizeRequest request = new NoteSynthesizeRequest();
        request.setWorkspaceId(workspaceId);
        request.setTopic("本周工作");
        request.setTemplate("retro");
        request.setDateFrom(weekStart);
        request.setDateTo(today);
        request.setSaveAsNote(true);

        try {
            NoteSynthesizeVO synthesized = aiService.synthesizeNotes(request, userId);
            if (synthesized.getCreatedNoteId() != null) {
                return new RetroResult(synthesized.getCreatedNoteId(), synthesized.getTitle());
            }
        } catch (BizException ex) {
            log.debug("Weekly retro synthesis fallback for user {}: {}", userId, ex.getMessage());
        }

        AiDailyReviewVO review = aiService.reviewDailyProgress(userId, workspaceId);
        String title = "每周复盘 · " + weekStart.format(TITLE_DATE) + " 至 " + today.format(TITLE_DATE);
        String content = buildReviewMarkdown(review, weekStart, today);
        NoteCreateRequest createRequest = new NoteCreateRequest();
        createRequest.setWorkspaceId(workspaceId);
        createRequest.setTitle(title);
        createRequest.setContent(content);
        NoteVO created = noteService.createNote(createRequest, userId);
        return new RetroResult(created.getId(), created.getTitle());
    }

    private String buildReviewMarkdown(AiDailyReviewVO review, LocalDate weekStart, LocalDate today) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 每周复盘\n\n");
        builder.append("周期：").append(weekStart).append(" 至 ").append(today).append("\n\n");
        builder.append("## 整体评价\n\n").append(defaultText(review.getSummary(), "本周工作已记录。")).append("\n\n");
        builder.append("## 完成亮点\n\n").append(formatBulletList(review.getHighlights(), "暂无亮点记录")).append("\n\n");
        builder.append("## 卡点与阻塞\n\n").append(formatBulletList(review.getBlockers(), "暂无")).append("\n\n");
        builder.append("## 下周关注\n\n").append(formatBulletList(review.getTomorrowFocus(), "待补充")).append("\n\n");
        builder.append("---\n\n*由 Noto 知微自动生成，可按需编辑。*\n");
        return builder.toString();
    }

    private String formatBulletList(List<String> items, String fallback) {
        if (items == null || items.isEmpty()) {
            return "- " + fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (StringUtils.hasText(item)) {
                builder.append("- ").append(item.trim()).append('\n');
            }
        }
        return builder.isEmpty() ? "- " + fallback : builder.toString().trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Long resolveWorkspaceId(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
            return workspaceId;
        }
        Long primary = userSettingService.resolvePrimaryWorkspaceId(userId);
        if (primary != null) {
            workspaceService.requireOwnedWorkspace(primary, userId);
            return primary;
        }
        return workspaceService.ensureDefaultWorkspace(userId);
    }

    private AiTaskEntity findThisWeekTask(Long userId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = start.plusDays(7);
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTaskEntity>()
                .eq(AiTaskEntity::getUserId, userId)
                .eq(AiTaskEntity::getTaskType, AiTaskType.WEEKLY_RETRO)
                .ge(AiTaskEntity::getCreatedAt, start)
                .lt(AiTaskEntity::getCreatedAt, end)
                .orderByDesc(AiTaskEntity::getCreatedAt)
                .last("LIMIT 1"));
    }

    private AiWeeklyRetroVO toRetroVO(AiTaskEntity task) {
        AiWeeklyRetroVO vo = new AiWeeklyRetroVO();
        vo.setTaskId(task.getId());
        vo.setWeekStart(task.getInputContent());
        vo.setGeneratedAt(task.getCreatedAt());
        if (!StringUtils.hasText(task.getOutputContent())) {
            return vo;
        }
        try {
            RetroPayload payload = objectMapper.readValue(task.getOutputContent(), RetroPayload.class);
            vo.setWeekStart(payload.getWeekStart());
            vo.setNoteId(payload.getNoteId());
            vo.setNoteTitle(payload.getNoteTitle());
        } catch (Exception ignored) {
            // keep defaults
        }
        return vo;
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private record RetroResult(Long noteId, String noteTitle) {}

    private static final class RetroPayload {
        private String weekStart;
        private Long noteId;
        private String noteTitle;

        public String getWeekStart() {
            return weekStart;
        }

        public void setWeekStart(String weekStart) {
            this.weekStart = weekStart;
        }

        public Long getNoteId() {
            return noteId;
        }

        public void setNoteId(Long noteId) {
            this.noteId = noteId;
        }

        public String getNoteTitle() {
            return noteTitle;
        }

        public void setNoteTitle(String noteTitle) {
            this.noteTitle = noteTitle;
        }
    }
}
