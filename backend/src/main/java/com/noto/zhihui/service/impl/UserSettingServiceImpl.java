package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.constants.AiUserSettingKeys;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.settings.AiUserSettingsUpdateRequest;
import com.noto.zhihui.entity.UserSettingEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.mapper.UserSettingMapper;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.settings.AiLightMemoryVO;
import com.noto.zhihui.vo.settings.AiUserSettingsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserSettingServiceImpl implements UserSettingService {

    private static final String DEFAULT_SCOPE = "workspace";
    private static final String DEFAULT_ANSWER_STYLE = "balanced";
    private static final int MAX_FOCUS_PROJECTS = 5;
    private static final int MAX_PROJECT_LENGTH = 48;
    private static final int MAX_AGENT_PREFERENCES_LENGTH = 240;

    private final UserSettingMapper userSettingMapper;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public UserSettingServiceImpl(
            UserSettingMapper userSettingMapper,
            WorkspaceService workspaceService,
            ObjectMapper objectMapper
    ) {
        this.userSettingMapper = userSettingMapper;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiUserSettingsVO getAiSettings(Long userId) {
        Map<String, String> values = loadMemoryRelatedValues(userId);
        AiUserSettingsVO vo = new AiUserSettingsVO();
        vo.setAutoSummaryOnSave(parseBoolean(values.get(AiUserSettingKeys.AUTO_SUMMARY_ON_SAVE), false));
        vo.setDefaultScope(normalizeScope(values.get(AiUserSettingKeys.DEFAULT_SCOPE)));
        vo.setAnswerStyle(normalizeAnswerStyle(values.get(AiUserSettingKeys.ANSWER_STYLE)));
        vo.setDailyDigestEnabled(parseBoolean(values.get(AiUserSettingKeys.DAILY_DIGEST_ENABLED), false));
        vo.setDailyDigestHour(parseHour(values.get(AiUserSettingKeys.DAILY_DIGEST_HOUR), 8));
        vo.setAgentTrustMode(parseBoolean(values.get(AiUserSettingKeys.AGENT_TRUST_MODE), false));
        vo.setWeeklyRetroEnabled(parseBoolean(values.get(AiUserSettingKeys.WEEKLY_RETRO_ENABLED), false));
        vo.setWeeklyRetroHour(parseHour(values.get(AiUserSettingKeys.WEEKLY_RETRO_HOUR), 17));
        vo.setAutoExtractTodosOnSave(parseBoolean(values.get(AiUserSettingKeys.AUTO_EXTRACT_TODOS_ON_SAVE), false));
        applyLightMemoryFields(vo, userId, values);
        return vo;
    }

    @Override
    @Transactional
    public AiUserSettingsVO updateAiSettings(Long userId, AiUserSettingsUpdateRequest request) {
        if (request.getAutoSummaryOnSave() != null) {
            upsert(userId, AiUserSettingKeys.AUTO_SUMMARY_ON_SAVE, String.valueOf(request.getAutoSummaryOnSave()));
        }
        if (StringUtils.hasText(request.getDefaultScope())) {
            upsert(userId, AiUserSettingKeys.DEFAULT_SCOPE, normalizeScope(request.getDefaultScope()));
        }
        if (StringUtils.hasText(request.getAnswerStyle())) {
            upsert(userId, AiUserSettingKeys.ANSWER_STYLE, normalizeAnswerStyle(request.getAnswerStyle()));
        }
        if (request.getDailyDigestEnabled() != null) {
            upsert(userId, AiUserSettingKeys.DAILY_DIGEST_ENABLED, String.valueOf(request.getDailyDigestEnabled()));
        }
        if (request.getDailyDigestHour() != null) {
            upsert(userId, AiUserSettingKeys.DAILY_DIGEST_HOUR, String.valueOf(clampHour(request.getDailyDigestHour())));
        }
        if (request.getAgentTrustMode() != null) {
            upsert(userId, AiUserSettingKeys.AGENT_TRUST_MODE, String.valueOf(request.getAgentTrustMode()));
        }
        if (request.getPrimaryWorkspaceId() != null) {
            Long workspaceId = request.getPrimaryWorkspaceId();
            if (workspaceId <= 0) {
                deleteSetting(userId, AiUserSettingKeys.PRIMARY_WORKSPACE_ID);
            } else {
                workspaceService.requireOwnedWorkspace(workspaceId, userId);
                upsert(userId, AiUserSettingKeys.PRIMARY_WORKSPACE_ID, String.valueOf(workspaceId));
            }
        }
        if (request.getFocusProjects() != null) {
            upsert(userId, AiUserSettingKeys.FOCUS_PROJECTS, serializeFocusProjects(request.getFocusProjects()));
        }
        if (request.getAgentPreferences() != null) {
            String normalized = normalizeAgentPreferences(request.getAgentPreferences());
            if (StringUtils.hasText(normalized)) {
                upsert(userId, AiUserSettingKeys.AGENT_PREFERENCES, normalized);
            } else {
                deleteSetting(userId, AiUserSettingKeys.AGENT_PREFERENCES);
            }
        }
        if (request.getWeeklyRetroEnabled() != null) {
            upsert(userId, AiUserSettingKeys.WEEKLY_RETRO_ENABLED, String.valueOf(request.getWeeklyRetroEnabled()));
        }
        if (request.getWeeklyRetroHour() != null) {
            upsert(userId, AiUserSettingKeys.WEEKLY_RETRO_HOUR, String.valueOf(clampHour(request.getWeeklyRetroHour())));
        }
        if (request.getAutoExtractTodosOnSave() != null) {
            upsert(userId, AiUserSettingKeys.AUTO_EXTRACT_TODOS_ON_SAVE, String.valueOf(request.getAutoExtractTodosOnSave()));
        }
        return getAiSettings(userId);
    }

    @Override
    public boolean isAgentTrustModeEnabled(Long userId) {
        Map<String, String> values = loadValues(userId, List.of(AiUserSettingKeys.AGENT_TRUST_MODE));
        return parseBoolean(values.get(AiUserSettingKeys.AGENT_TRUST_MODE), false);
    }

    @Override
    public String resolveAnswerStyleInstruction(Long userId) {
        Map<String, String> values = loadValues(userId, List.of(AiUserSettingKeys.ANSWER_STYLE));
        return answerStyleInstruction(normalizeAnswerStyle(values.get(AiUserSettingKeys.ANSWER_STYLE)));
    }

    @Override
    public AiLightMemoryVO getLightMemory(Long userId) {
        Map<String, String> values = loadValues(userId, List.of(
                AiUserSettingKeys.PRIMARY_WORKSPACE_ID,
                AiUserSettingKeys.FOCUS_PROJECTS,
                AiUserSettingKeys.AGENT_PREFERENCES
        ));
        AiLightMemoryVO memory = new AiLightMemoryVO();
        Long workspaceId = parseLong(values.get(AiUserSettingKeys.PRIMARY_WORKSPACE_ID));
        memory.setPrimaryWorkspaceId(workspaceId);
        memory.setPrimaryWorkspaceName(resolveWorkspaceName(workspaceId, userId));
        memory.setFocusProjects(parseFocusProjects(values.get(AiUserSettingKeys.FOCUS_PROJECTS)));
        memory.setAgentPreferences(normalizeAgentPreferences(values.get(AiUserSettingKeys.AGENT_PREFERENCES)));
        return memory;
    }

    @Override
    public String resolveLightMemoryPromptBlock(Long userId) {
        return getLightMemory(userId).toPromptBlock();
    }

    @Override
    public Long resolvePrimaryWorkspaceId(Long userId) {
        Map<String, String> values = loadValues(userId, List.of(AiUserSettingKeys.PRIMARY_WORKSPACE_ID));
        return parseLong(values.get(AiUserSettingKeys.PRIMARY_WORKSPACE_ID));
    }

    @Override
    public List<Long> listUserIdsForWeeklyRetroHour(int hour) {
        int normalizedHour = clampHour(hour);
        List<UserSettingEntity> enabledRows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getSettingKey, AiUserSettingKeys.WEEKLY_RETRO_ENABLED)
                .in(UserSettingEntity::getSettingValue, List.of("true", "1")));
        if (enabledRows.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = enabledRows.stream().map(UserSettingEntity::getUserId).distinct().toList();
        List<UserSettingEntity> hourRows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getSettingKey, AiUserSettingKeys.WEEKLY_RETRO_HOUR)
                .in(UserSettingEntity::getUserId, userIds));
        Map<Long, Integer> hourByUser = hourRows.stream()
                .collect(Collectors.toMap(
                        UserSettingEntity::getUserId,
                        row -> parseHour(row.getSettingValue(), 17),
                        (a, b) -> a
                ));
        return userIds.stream()
                .filter(userId -> hourByUser.getOrDefault(userId, 17) == normalizedHour)
                .toList();
    }

    @Override
    public boolean isAutoExtractTodosOnSaveEnabled(Long userId) {
        Map<String, String> values = loadValues(userId, List.of(AiUserSettingKeys.AUTO_EXTRACT_TODOS_ON_SAVE));
        return parseBoolean(values.get(AiUserSettingKeys.AUTO_EXTRACT_TODOS_ON_SAVE), false);
    }

    @Override
    public List<Long> listUserIdsForDailyDigestHour(int hour) {
        int normalizedHour = clampHour(hour);
        List<UserSettingEntity> enabledRows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getSettingKey, AiUserSettingKeys.DAILY_DIGEST_ENABLED)
                .in(UserSettingEntity::getSettingValue, List.of("true", "1")));
        if (enabledRows.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = enabledRows.stream().map(UserSettingEntity::getUserId).distinct().toList();
        List<UserSettingEntity> hourRows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getSettingKey, AiUserSettingKeys.DAILY_DIGEST_HOUR)
                .in(UserSettingEntity::getUserId, userIds));
        Map<Long, Integer> hourByUser = hourRows.stream()
                .collect(Collectors.toMap(
                        UserSettingEntity::getUserId,
                        row -> parseHour(row.getSettingValue(), 8),
                        (a, b) -> a
                ));
        return userIds.stream()
                .filter(userId -> hourByUser.getOrDefault(userId, 8) == normalizedHour)
                .toList();
    }

    private Map<String, String> loadMemoryRelatedValues(Long userId) {
        return loadValues(userId, List.of(
                AiUserSettingKeys.AUTO_SUMMARY_ON_SAVE,
                AiUserSettingKeys.DEFAULT_SCOPE,
                AiUserSettingKeys.ANSWER_STYLE,
                AiUserSettingKeys.DAILY_DIGEST_ENABLED,
                AiUserSettingKeys.DAILY_DIGEST_HOUR,
                AiUserSettingKeys.AGENT_TRUST_MODE,
                AiUserSettingKeys.PRIMARY_WORKSPACE_ID,
                AiUserSettingKeys.FOCUS_PROJECTS,
                AiUserSettingKeys.AGENT_PREFERENCES,
                AiUserSettingKeys.WEEKLY_RETRO_ENABLED,
                AiUserSettingKeys.WEEKLY_RETRO_HOUR,
                AiUserSettingKeys.AUTO_EXTRACT_TODOS_ON_SAVE
        ));
    }

    private void applyLightMemoryFields(AiUserSettingsVO vo, Long userId, Map<String, String> values) {
        Long workspaceId = parseLong(values.get(AiUserSettingKeys.PRIMARY_WORKSPACE_ID));
        vo.setPrimaryWorkspaceId(workspaceId);
        vo.setPrimaryWorkspaceName(resolveWorkspaceName(workspaceId, userId));
        vo.setFocusProjects(parseFocusProjects(values.get(AiUserSettingKeys.FOCUS_PROJECTS)));
        vo.setAgentPreferences(normalizeAgentPreferences(values.get(AiUserSettingKeys.AGENT_PREFERENCES)));
    }

    private String resolveWorkspaceName(Long workspaceId, Long userId) {
        if (workspaceId == null || workspaceId <= 0) {
            return null;
        }
        try {
            WorkspaceEntity workspace = workspaceService.requireOwnedWorkspace(workspaceId, userId);
            return workspace.getName();
        } catch (BizException ex) {
            return null;
        }
    }

    private List<String> parseFocusProjects(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(raw.trim(), new TypeReference<List<String>>() {});
            return normalizeFocusProjects(items);
        } catch (Exception ex) {
            List<String> fallback = new ArrayList<>();
            for (String line : raw.split("[\\n,，;；]+")) {
                if (StringUtils.hasText(line)) {
                    fallback.add(line.trim());
                }
            }
            return normalizeFocusProjects(fallback);
        }
    }

    private List<String> normalizeFocusProjects(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : items) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.length() > MAX_PROJECT_LENGTH) {
                trimmed = trimmed.substring(0, MAX_PROJECT_LENGTH);
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
            if (normalized.size() >= MAX_FOCUS_PROJECTS) {
                break;
            }
        }
        return normalized;
    }

    private String serializeFocusProjects(List<String> items) {
        List<String> normalized = normalizeFocusProjects(items);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "主攻项目格式无效");
        }
    }

    private String normalizeAgentPreferences(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_AGENT_PREFERENCES_LENGTH) {
            return trimmed.substring(0, MAX_AGENT_PREFERENCES_LENGTH);
        }
        return trimmed;
    }

    private Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void deleteSetting(Long userId, String key) {
        userSettingMapper.delete(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getUserId, userId)
                .eq(UserSettingEntity::getSettingKey, key));
    }

    private int parseHour(String raw, int fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return clampHour(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int clampHour(int hour) {
        if (hour < 0) {
            return 0;
        }
        if (hour > 23) {
            return 23;
        }
        return hour;
    }

    private String answerStyleInstruction(String style) {
        return switch (style) {
            case "concise" -> "回答风格：简洁扼要，优先给出结论与要点，避免冗长铺垫。";
            case "detailed" -> "回答风格：详细充分，适当展开背景、推理过程与示例，帮助用户深入理解。";
            default -> "回答风格：平衡简洁与细节，先给结论再补充必要说明。";
        };
    }

    private Map<String, String> loadValues(Long userId, List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<UserSettingEntity> rows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getUserId, userId)
                .in(UserSettingEntity::getSettingKey, keys));
        return rows.stream()
                .collect(Collectors.toMap(UserSettingEntity::getSettingKey, UserSettingEntity::getSettingValue, (a, b) -> b));
    }

    private void upsert(Long userId, String key, String value) {
        UserSettingEntity existing = userSettingMapper.selectOne(new LambdaQueryWrapper<UserSettingEntity>()
                .eq(UserSettingEntity::getUserId, userId)
                .eq(UserSettingEntity::getSettingKey, key)
                .last("LIMIT 1"));
        if (existing == null) {
            UserSettingEntity entity = new UserSettingEntity();
            entity.setUserId(userId);
            entity.setSettingKey(key);
            entity.setSettingValue(value);
            userSettingMapper.insert(entity);
            return;
        }
        existing.setSettingValue(value);
        userSettingMapper.updateById(existing);
    }

    private boolean parseBoolean(String raw, boolean fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }

    private String normalizeScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_SCOPE;
        }
        return "note".equalsIgnoreCase(raw.trim()) ? "note" : DEFAULT_SCOPE;
    }

    private String normalizeAnswerStyle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_ANSWER_STYLE;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "concise", "detailed" -> normalized;
            default -> DEFAULT_ANSWER_STYLE;
        };
    }
}
