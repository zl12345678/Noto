package com.noto.zhihui.controller.workspace;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.workspace.WorkspaceCreateRequest;
import com.noto.zhihui.dto.workspace.WorkspaceUpdateRequest;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.workspace.WorkspaceVO;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceVO>> list() {
        Long userId = requireUserId();
        List<WorkspaceVO> workspaces = workspaceService.lambdaQuery()
                .eq(WorkspaceEntity::getOwnerUserId, userId)
                .orderByAsc(WorkspaceEntity::getCreatedAt)
                .list()
                .stream()
                .map(workspace -> toVO(workspace))
                .toList();
        return ApiResponse.success(workspaces, null);
    }

    @Transactional
    @PostMapping
    public ApiResponse<WorkspaceVO> create(@Valid @RequestBody WorkspaceCreateRequest request) {
        Long userId = requireUserId();
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setOwnerUserId(userId);
        workspace.setName(request.getName());
        workspace.setType(0);
        workspace.setDescription(request.getDescription());
        workspace.setStatus(0);
        workspaceService.save(workspace);
        return ApiResponse.success(toVO(workspace), null);
    }

    @Transactional
    @PutMapping("/{id}")
    public ApiResponse<WorkspaceVO> update(@PathVariable Long id, @Valid @RequestBody WorkspaceUpdateRequest request) {
        Long userId = requireUserId();
        WorkspaceEntity workspace = workspaceService.updateWorkspace(
                id,
                userId,
                request.getName(),
                request.getDescription()
        );
        return ApiResponse.success(toVO(workspace), null);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workspaceService.deleteWorkspace(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private WorkspaceVO toVO(WorkspaceEntity workspace) {
        return new WorkspaceVO(
                workspace.getId(),
                workspace.getName(),
                workspace.getType(),
                workspace.getDescription(),
                workspace.getStatus(),
                workspace.getCreatedAt(),
                workspace.getHomeNoteId()
        );
    }
}
