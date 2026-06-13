package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.entity.NoteTagEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.mapper.WorkspaceMapper;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.service.WorkspaceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkspaceServiceImpl extends ServiceImpl<WorkspaceMapper, WorkspaceEntity> implements WorkspaceService {

    private static final String DEFAULT_WORKSPACE_NAME = "我的空间";
    private static final String DEFAULT_FOLDER_NAME = "默认文件夹";

    private final NoteFolderService noteFolderService;
    private final NoteService noteService;
    private final TagService tagService;
    private final NoteTagService noteTagService;

    public WorkspaceServiceImpl(
            @Lazy NoteFolderService noteFolderService,
            @Lazy NoteService noteService,
            @Lazy TagService tagService,
            @Lazy NoteTagService noteTagService
    ) {
        this.noteFolderService = noteFolderService;
        this.noteService = noteService;
        this.tagService = tagService;
        this.noteTagService = noteTagService;
    }

    @Override
    public Long ensureDefaultWorkspace(Long userId) {
        WorkspaceEntity workspace = query()
                .eq("owner_user_id", userId)
                .orderByAsc("created_at")
                .last("LIMIT 1")
                .one();
        if (workspace != null) {
            return workspace.getId();
        }
        return createDefaultWorkspace(userId).getId();
    }

    @Override
    @Transactional
    public WorkspaceEntity createDefaultWorkspace(Long userId) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setOwnerUserId(userId);
        workspace.setName(DEFAULT_WORKSPACE_NAME);
        workspace.setType(0);
        workspace.setDescription("个人默认工作区");
        workspace.setStatus(0);
        save(workspace);

        NoteFolderEntity folder = new NoteFolderEntity();
        folder.setWorkspaceId(workspace.getId());
        folder.setName(DEFAULT_FOLDER_NAME);
        folder.setSortOrder(0);
        noteFolderService.save(folder);

        return getById(workspace.getId());
    }

    @Override
    public WorkspaceEntity requireOwnedWorkspace(Long workspaceId, Long userId) {
        WorkspaceEntity workspace = getById(workspaceId);
        if (workspace == null || !userId.equals(workspace.getOwnerUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return workspace;
    }

    @Override
    public WorkspaceEntity updateWorkspace(Long workspaceId, Long userId, String name, String description) {
        WorkspaceEntity workspace = requireOwnedWorkspace(workspaceId, userId);
        workspace.setName(name);
        workspace.setDescription(description);
        updateById(workspace);
        return workspace;
    }

    @Override
    @Transactional
    public void deleteWorkspace(Long workspaceId, Long userId) {
        requireOwnedWorkspace(workspaceId, userId);
        long workspaceCount = query()
                .eq("owner_user_id", userId)
                .count();
        if (workspaceCount <= 1) {
            throw new BizException(ErrorCode.WORKSPACE_LAST_ONE);
        }

        List<Long> noteIds = noteService.lambdaQuery()
                .eq(NoteEntity::getWorkspaceId, workspaceId)
                .list()
                .stream()
                .map(NoteEntity::getId)
                .toList();
        if (!noteIds.isEmpty()) {
            noteTagService.lambdaUpdate()
                    .in(NoteTagEntity::getNoteId, noteIds)
                    .remove();
            noteService.lambdaUpdate()
                    .eq(NoteEntity::getWorkspaceId, workspaceId)
                    .remove();
        }

        noteFolderService.lambdaUpdate()
                .eq(NoteFolderEntity::getWorkspaceId, workspaceId)
                .remove();
        tagService.lambdaUpdate()
                .eq(TagEntity::getWorkspaceId, workspaceId)
                .remove();
        removeById(workspaceId);
    }
}
