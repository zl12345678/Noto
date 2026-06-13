package com.noto.zhihui.service;

import com.noto.zhihui.dto.ai.AiChatSessionCreateRequest;
import com.noto.zhihui.entity.AiChatSessionEntity;
import com.noto.zhihui.vo.ai.AiChatMessageVO;
import com.noto.zhihui.vo.ai.AiChatSessionVO;
import com.noto.zhihui.vo.ai.AiReferenceVO;

import java.util.List;

public interface AiChatSessionService {

    List<AiChatSessionVO> listSessions(Long userId, Long workspaceId, int limit);

    AiChatSessionVO createSession(AiChatSessionCreateRequest request, Long userId);

    AiChatSessionEntity requireOwnedSession(Long sessionId, Long userId);

    List<AiChatMessageVO> listMessages(Long sessionId, Long userId);

    void deleteSession(Long sessionId, Long userId);

    void saveExchange(
            Long sessionId,
            Long userId,
            String question,
            String answer,
            List<AiReferenceVO> references,
            String modelName
    );

    /** 加载会话最近若干条消息，格式化为「用户/助手」对话块 */
    String buildRecentDialogBlock(Long sessionId, Long userId, int maxMessages);
}
