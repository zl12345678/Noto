package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.service.AiWeeklyRetroService;
import com.noto.zhihui.vo.ai.AiWeeklyRetroVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiWeeklyRetroServiceImpl implements AiWeeklyRetroService {

    @Override
    public AiWeeklyRetroVO getThisWeekRetro(Long userId) {
        return null;
    }

    @Override
    public AiWeeklyRetroVO generateWeeklyRetro(Long userId, Long workspaceId) {
        throw new BizException(ErrorCode.AI_ERROR.getCode(), "AI 功能未启用");
    }

    @Override
    public void runScheduledWeeklyRetros(int hour) {
        // no-op
    }
}
