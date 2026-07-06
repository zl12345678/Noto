package com.noto.zhihui.controller.ai;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.AiAgentConfirmRequest;
import com.noto.zhihui.dto.ai.AiAgentPlanRequest;
import com.noto.zhihui.dto.ai.AiAgentStepUpdateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.AiAgentService;
import com.noto.zhihui.vo.ai.AiAgentTaskVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/agent")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/plan")
    public ApiResponse<AiAgentTaskVO> plan(@Valid @RequestBody AiAgentPlanRequest request) {
        return ApiResponse.success(aiAgentService.plan(request, requireUserId()), null);
    }

    @PostMapping("/tasks/{id}/confirm")
    public ApiResponse<AiAgentTaskVO> confirm(
            @PathVariable Long id,
            @Valid @RequestBody AiAgentConfirmRequest request
    ) {
        return ApiResponse.success(aiAgentService.confirm(id, request, requireUserId()), null);
    }

    @PatchMapping("/tasks/{id}/steps/{stepId}")
    public ApiResponse<AiAgentTaskVO> updateStep(
            @PathVariable Long id,
            @PathVariable String stepId,
            @Valid @RequestBody AiAgentStepUpdateRequest request
    ) {
        return ApiResponse.success(aiAgentService.updateStep(id, stepId, request, requireUserId()), null);
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<AiAgentTaskVO> getTask(@PathVariable Long id) {
        return ApiResponse.success(aiAgentService.getTask(id, requireUserId()), null);
    }

    @GetMapping("/tasks")
    public ApiResponse<Page<AiAgentTaskVO>> listTasks(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.success(aiAgentService.listTasks(requireUserId(), workspaceId, page, size), null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
