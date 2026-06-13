package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.entity.WorkspaceEntity;

public interface WorkspaceService extends IService<WorkspaceEntity> {

    Long ensureDefaultWorkspace(Long userId);

    WorkspaceEntity createDefaultWorkspace(Long userId);

    WorkspaceEntity requireOwnedWorkspace(Long workspaceId, Long userId);

    WorkspaceEntity updateWorkspace(Long workspaceId, Long userId, String name, String description);

    void deleteWorkspace(Long workspaceId, Long userId);
}
