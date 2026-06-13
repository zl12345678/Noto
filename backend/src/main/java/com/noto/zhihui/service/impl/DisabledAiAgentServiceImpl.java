package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.AiAgentConfirmRequest;
import com.noto.zhihui.dto.ai.AiAgentPlanRequest;
import com.noto.zhihui.service.AiAgentService;
import com.noto.zhihui.vo.ai.AiAgentTaskVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiAgentServiceImpl implements AiAgentService {

    @Override
    public AiAgentTaskVO plan(AiAgentPlanRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public AiAgentTaskVO confirm(Long taskId, AiAgentConfirmRequest request, Long userId) {
        throw disabled();
    }

    @Override
    public AiAgentTaskVO getTask(Long taskId, Long userId) {
        throw disabled();
    }

    @Override
    public Page<AiAgentTaskVO> listTasks(Long userId, Long workspaceId, long page, long size) {
        throw disabled();
    }

    private BizException disabled() {
        return new BizException(
                ErrorCode.AI_ERROR.getCode(),
                "AI 未启用。请设置 NOTO_AI_ENABLED=true 和 AI_DASHSCOPE_API_KEY 后重启后端"
        );
    }
}
