package com.noto.zhihui.controller.ai;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.NoteRagIndexService;
import com.noto.zhihui.vo.ai.NoteRagIndexStatusVO;
import com.noto.zhihui.vo.ai.NoteRagReindexVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/rag")
public class AiRagController {

    private final NoteRagIndexService noteRagIndexService;

    public AiRagController(NoteRagIndexService noteRagIndexService) {
        this.noteRagIndexService = noteRagIndexService;
    }

    @GetMapping("/status")
    public ApiResponse<NoteRagIndexStatusVO> status(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(noteRagIndexService.indexStatus(requireUserId(), workspaceId), null);
    }

    @PostMapping("/reindex")
    public ApiResponse<NoteRagReindexVO> reindexWorkspace(@RequestParam(required = false) Long workspaceId) {
        return ApiResponse.success(noteRagIndexService.reindexWorkspace(requireUserId(), workspaceId), null);
    }

    @PostMapping("/notes/{id}/reindex")
    public ApiResponse<NoteRagReindexVO> reindexNote(@PathVariable Long id) {
        return ApiResponse.success(noteRagIndexService.reindexNote(id, requireUserId()), null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
