package com.noto.zhihui.controller.ai;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.ai.AiChatSessionCreateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.AiChatSessionService;
import com.noto.zhihui.vo.ai.AiChatMessageVO;
import com.noto.zhihui.vo.ai.AiChatSessionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/sessions")
public class AiChatSessionController {

    private final AiChatSessionService aiChatSessionService;

    public AiChatSessionController(AiChatSessionService aiChatSessionService) {
        this.aiChatSessionService = aiChatSessionService;
    }

    @GetMapping
    public ApiResponse<List<AiChatSessionVO>> list(
            @RequestParam Long workspaceId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(
                aiChatSessionService.listSessions(requireUserId(), workspaceId, limit),
                null
        );
    }

    @PostMapping
    public ApiResponse<AiChatSessionVO> create(@Valid @RequestBody AiChatSessionCreateRequest request) {
        return ApiResponse.success(aiChatSessionService.createSession(request, requireUserId()), null);
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<AiChatMessageVO>> messages(@PathVariable Long id) {
        return ApiResponse.success(aiChatSessionService.listMessages(id, requireUserId()), null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiChatSessionService.deleteSession(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
