package com.noto.zhihui.controller.folder;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.folder.FolderCreateRequest;
import com.noto.zhihui.dto.folder.FolderTreeMoveRequest;
import com.noto.zhihui.dto.folder.FolderUpdateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.folder.FolderVO;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    private final NoteFolderService noteFolderService;
    private final NoteService noteService;
    private final WorkspaceService workspaceService;

    public FolderController(
            NoteFolderService noteFolderService,
            NoteService noteService,
            WorkspaceService workspaceService
    ) {
        this.noteFolderService = noteFolderService;
        this.noteService = noteService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<FolderVO>> list(@RequestParam Long workspaceId) {
        workspaceService.requireOwnedWorkspace(workspaceId, requireUserId());
        List<FolderVO> folders = noteFolderService.lambdaQuery()
                .eq(NoteFolderEntity::getWorkspaceId, workspaceId)
                .orderByDesc(NoteFolderEntity::getSortOrder)
                .orderByAsc(NoteFolderEntity::getCreatedAt)
                .list()
                .stream()
                .map(this::toVO)
                .toList();
        return ApiResponse.success(folders, null);
    }

    @Transactional
    @PostMapping
    public ApiResponse<FolderVO> create(@Valid @RequestBody FolderCreateRequest request) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), requireUserId());
        validateParentFolder(request.getWorkspaceId(), request.getParentId());
        NoteFolderEntity folder = new NoteFolderEntity();
        folder.setWorkspaceId(request.getWorkspaceId());
        folder.setParentId(request.getParentId());
        folder.setName(request.getName());
        folder.setSortOrder(0);
        noteFolderService.save(folder);
        return ApiResponse.success(toVO(folder), null);
    }

    @Transactional
    @PutMapping("/{id}")
    public ApiResponse<FolderVO> update(@PathVariable Long id, @Valid @RequestBody FolderUpdateRequest request) {
        NoteFolderEntity folder = loadOwnedFolder(id);
        folder.setName(request.getName());
        if (request.getParentId() != null) {
            validateParentFolder(folder.getWorkspaceId(), request.getParentId());
            folder.setParentId(request.getParentId());
        }
        if (request.getSortOrder() != null) {
            folder.setSortOrder(request.getSortOrder());
        }
        noteFolderService.updateById(folder);
        return ApiResponse.success(toVO(folder), null);
    }

    @Transactional
    @PatchMapping("/{id}/tree")
    public ApiResponse<FolderVO> moveTree(@PathVariable Long id, @Valid @RequestBody FolderTreeMoveRequest request) {
        NoteFolderEntity folder = loadOwnedFolder(id);
        Long newParentId = request.getParentId();
        if (newParentId != null) {
            if (newParentId.equals(folder.getId())) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            NoteFolderEntity parent = noteFolderService.getById(newParentId);
            if (parent == null || !parent.getWorkspaceId().equals(folder.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            if (isFolderDescendant(folder.getId(), newParentId)) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
        }
        folder.setParentId(newParentId);
        folder.setSortOrder(request.getSortOrder());
        noteFolderService.lambdaUpdate()
                .eq(NoteFolderEntity::getId, folder.getId())
                .set(NoteFolderEntity::getParentId, folder.getParentId())
                .set(NoteFolderEntity::getSortOrder, folder.getSortOrder())
                .update();
        return ApiResponse.success(toVO(folder), null);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        NoteFolderEntity folder = loadOwnedFolder(id);
        long noteCount = noteService.lambdaQuery()
                .eq(NoteEntity::getFolderId, folder.getId())
                .count();
        if (noteCount > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        noteFolderService.removeById(id);
        return ApiResponse.success(null, null);
    }

    private void validateParentFolder(Long workspaceId, Long parentId) {
        if (parentId == null) {
            return;
        }
        NoteFolderEntity parent = noteFolderService.getById(parentId);
        if (parent == null || !workspaceId.equals(parent.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "父文件夹不存在或不属于当前知识库");
        }
    }

    private NoteFolderEntity loadOwnedFolder(Long id) {
        NoteFolderEntity folder = noteFolderService.getById(id);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        workspaceService.requireOwnedWorkspace(folder.getWorkspaceId(), requireUserId());
        return folder;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private FolderVO toVO(NoteFolderEntity folder) {
        return new FolderVO(
                folder.getId(),
                folder.getWorkspaceId(),
                folder.getParentId(),
                folder.getName(),
                folder.getSortOrder(),
                folder.getCreatedAt()
        );
    }

    private boolean isFolderDescendant(Long ancestorFolderId, Long candidateId) {
        Long current = candidateId;
        while (current != null) {
            if (current.equals(ancestorFolderId)) {
                return true;
            }
            NoteFolderEntity node = noteFolderService.getById(current);
            if (node == null) {
                break;
            }
            current = node.getParentId();
        }
        return false;
    }
}
