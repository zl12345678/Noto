package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.service.AiDigestService;
import com.noto.zhihui.vo.ai.AiDigestVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiDigestServiceImpl implements AiDigestService {

    @Override
    public AiDigestVO getTodayDigest(Long userId) {
        return null;
    }

    @Override
    public AiDigestVO generateDigest(Long userId, Long workspaceId) {
        throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 功能未启用");
    }

    @Override
    public void runScheduledDigests(int hour) {
        // no-op
    }
}
