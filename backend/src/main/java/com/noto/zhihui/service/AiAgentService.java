package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.dto.ai.AiAgentConfirmRequest;
import com.noto.zhihui.dto.ai.AiAgentPlanRequest;
import com.noto.zhihui.vo.ai.AiAgentTaskVO;

public interface AiAgentService {

    AiAgentTaskVO plan(AiAgentPlanRequest request, Long userId);

    AiAgentTaskVO confirm(Long taskId, AiAgentConfirmRequest request, Long userId);

    AiAgentTaskVO getTask(Long taskId, Long userId);

    Page<AiAgentTaskVO> listTasks(Long userId, Long workspaceId, long page, long size);
}
