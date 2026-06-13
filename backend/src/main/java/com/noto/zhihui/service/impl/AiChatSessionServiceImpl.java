package com.noto.zhihui.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.AiConversationContext;
import com.noto.zhihui.dto.ai.AiChatSessionCreateRequest;
import com.noto.zhihui.entity.AiChatMessageEntity;
import com.noto.zhihui.entity.AiChatSessionEntity;
import com.noto.zhihui.mapper.AiChatMessageMapper;
import com.noto.zhihui.mapper.AiChatSessionMapper;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.ai.AiChatMessageVO;
import com.noto.zhihui.vo.ai.AiChatSessionVO;
import com.noto.zhihui.vo.ai.AiReferenceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiChatSessionServiceImpl implements AiChatSessionService {

    private static final int TITLE_MAX = 80;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public AiChatSessionServiceImpl(
            AiChatSessionMapper sessionMapper,
            AiChatMessageMapper messageMapper,
            WorkspaceService workspaceService,
            ObjectMapper objectMapper
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AiChatSessionVO> listSessions(Long userId, Long workspaceId, int limit) {
        workspaceService.requireOwnedWorkspace(workspaceId, userId);
        int size = Math.min(Math.max(limit, 1), 50);
        return sessionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatSessionEntity>()
                        .eq(AiChatSessionEntity::getUserId, userId)
                        .eq(AiChatSessionEntity::getWorkspaceId, workspaceId)
                        .orderByDesc(AiChatSessionEntity::getLatestMessageAt)
                        .orderByDesc(AiChatSessionEntity::getCreatedAt)
                        .last("LIMIT " + size)
        ).stream().map(this::toSessionVO).toList();
    }

    @Override
    @Transactional
    public AiChatSessionVO createSession(AiChatSessionCreateRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        AiChatSessionEntity entity = new AiChatSessionEntity();
        entity.setWorkspaceId(request.getWorkspaceId());
        entity.setUserId(userId);
        entity.setSessionType(buildSessionType(request.getScope(), request.getTargetId()));
        entity.setTitle(normalizeTitle(request.getTitle()));
        entity.setLatestMessageAt(LocalDateTime.now());
        sessionMapper.insert(entity);
        return toSessionVO(entity);
    }

    @Override
    public AiChatSessionEntity requireOwnedSession(Long sessionId, Long userId) {
        AiChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return session;
    }

    @Override
    public List<AiChatMessageVO> listMessages(Long sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        return messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessageEntity>()
                        .eq(AiChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(AiChatMessageEntity::getCreatedAt)
        ).stream().map(this::toMessageVO).toList();
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        messageMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessageEntity>()
                        .eq(AiChatMessageEntity::getSessionId, sessionId)
        );
        sessionMapper.deleteById(sessionId);
    }

    @Override
    @Transactional
    public void saveExchange(
            Long sessionId,
            Long userId,
            String question,
            String answer,
            List<AiReferenceVO> references,
            String modelName
    ) {
        AiChatSessionEntity session = requireOwnedSession(sessionId, userId);
        LocalDateTime now = LocalDateTime.now();

        AiChatMessageEntity userMessage = new AiChatMessageEntity();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(question);
        userMessage.setTokenUsage(0);
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        AiChatMessageEntity assistantMessage = new AiChatMessageEntity();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer == null ? "" : answer);
        assistantMessage.setTokenUsage(0);
        assistantMessage.setModelName(modelName);
        assistantMessage.setReferencedNoteIds(serializeReferences(references));
        assistantMessage.setCreatedAt(now.plusNanos(1));
        messageMapper.insert(assistantMessage);

        if (!StringUtils.hasText(session.getTitle()) || "新对话".equals(session.getTitle())) {
            session.setTitle(normalizeTitle(question));
        }
        session.setLatestMessageAt(now);
        sessionMapper.updateById(session);
    }

    @Override
    public String buildRecentDialogBlock(Long sessionId, Long userId, int maxMessages) {
        if (sessionId == null) {
            return "";
        }
        requireOwnedSession(sessionId, userId);
        int size = Math.min(Math.max(maxMessages, 1), 12);
        List<AiChatMessageEntity> entities = messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessageEntity>()
                        .eq(AiChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(AiChatMessageEntity::getCreatedAt)
                        .last("LIMIT " + size)
        );
        if (entities.isEmpty()) {
            return "";
        }
        List<AiChatMessageVO> messages = new java.util.ArrayList<>(entities.size());
        for (int i = entities.size() - 1; i >= 0; i--) {
            messages.add(toMessageVO(entities.get(i)));
        }
        return AiConversationContext.formatDialogBlock(messages);
    }

    private AiChatSessionVO toSessionVO(AiChatSessionEntity entity) {
        SessionScope scope = parseSessionType(entity.getSessionType());
        return new AiChatSessionVO(
                entity.getId(),
                entity.getWorkspaceId(),
                scope.scope(),
                scope.targetId(),
                entity.getTitle(),
                entity.getLatestMessageAt(),
                entity.getCreatedAt()
        );
    }

    private AiChatMessageVO toMessageVO(AiChatMessageEntity entity) {
        return new AiChatMessageVO(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                deserializeReferences(entity.getReferencedNoteIds()),
                entity.getCreatedAt()
        );
    }

    private String buildSessionType(String scope, Long targetId) {
        if ("note".equalsIgnoreCase(scope) && targetId != null) {
            return "note_ask:" + targetId;
        }
        return "workspace_ask";
    }

    private SessionScope parseSessionType(String sessionType) {
        if (sessionType != null && sessionType.startsWith("note_ask:")) {
            String idPart = sessionType.substring("note_ask:".length());
            try {
                return new SessionScope("note", Long.parseLong(idPart));
            } catch (NumberFormatException ignored) {
                return new SessionScope("note", null);
            }
        }
        return new SessionScope("workspace", null);
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "新对话";
        }
        String trimmed = title.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= TITLE_MAX ? trimmed : trimmed.substring(0, TITLE_MAX) + "…";
    }

    private String serializeReferences(List<AiReferenceVO> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<AiReferenceVO> deserializeReferences(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            if (raw.trim().startsWith("[")) {
                return objectMapper.readValue(raw, new TypeReference<List<AiReferenceVO>>() {});
            }
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record SessionScope(String scope, Long targetId) {}
}
