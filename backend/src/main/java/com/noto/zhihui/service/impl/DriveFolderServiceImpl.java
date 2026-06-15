package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.drive.DriveFolderCreateRequest;
import com.noto.zhihui.dto.drive.DriveFolderUpdateRequest;
import com.noto.zhihui.entity.DriveFolderEntity;
import com.noto.zhihui.entity.NoteAttachmentEntity;
import com.noto.zhihui.mapper.DriveFolderMapper;
import com.noto.zhihui.mapper.NoteAttachmentMapper;
import com.noto.zhihui.service.DriveFolderService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.drive.DriveFolderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DriveFolderServiceImpl extends ServiceImpl<DriveFolderMapper, DriveFolderEntity> implements DriveFolderService {

    private final WorkspaceService workspaceService;
    private final NoteAttachmentMapper attachmentMapper;

    public DriveFolderServiceImpl(WorkspaceService workspaceService, NoteAttachmentMapper attachmentMapper) {
        this.workspaceService = workspaceService;
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public List<DriveFolderVO> listFolders(Long workspaceId, Long userId, String keyword) {
        workspaceService.requireOwnedWorkspace(workspaceId, userId);
        LambdaQueryWrapper<DriveFolderEntity> query = new LambdaQueryWrapper<DriveFolderEntity>()
                .eq(DriveFolderEntity::getWorkspaceId, workspaceId)
                .orderByDesc(DriveFolderEntity::getSortOrder)
                .orderByAsc(DriveFolderEntity::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            query.like(DriveFolderEntity::getName, keyword.trim());
        }
        List<DriveFolderEntity> folders = list(query);
        Map<Long, Long> counts = attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                        .eq(NoteAttachmentEntity::getWorkspaceId, workspaceId)
                        .isNotNull(NoteAttachmentEntity::getFolderId))
                .stream()
                .collect(Collectors.groupingBy(NoteAttachmentEntity::getFolderId, Collectors.counting()));
        return folders.stream()
                .map(folder -> toVO(folder, counts.getOrDefault(folder.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public DriveFolderVO createFolder(DriveFolderCreateRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        String name = request.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件夹名称不能为空");
        }
        Long parentId = request.getParentId();
        if (parentId != null) {
            DriveFolderEntity parent = requireOwnedFolder(parentId, userId);
            if (!Objects.equals(parent.getWorkspaceId(), request.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "父文件夹与知识库不匹配");
            }
        }
        DriveFolderEntity folder = new DriveFolderEntity();
        folder.setWorkspaceId(request.getWorkspaceId());
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setSortOrder(0);
        save(folder);
        return toVO(folder, 0L);
    }

    @Override
    @Transactional
    public DriveFolderVO updateFolder(Long folderId, DriveFolderUpdateRequest request, Long userId) {
        DriveFolderEntity folder = requireOwnedFolder(folderId, userId);
        String name = request.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件夹名称不能为空");
        }
        folder.setName(name);
        updateById(folder);
        long count = attachmentMapper.selectCount(new LambdaQueryWrapper<NoteAttachmentEntity>()
                .eq(NoteAttachmentEntity::getFolderId, folderId));
        return toVO(folder, count);
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        DriveFolderEntity folder = requireOwnedFolder(folderId, userId);
        List<NoteAttachmentEntity> files = attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                .eq(NoteAttachmentEntity::getFolderId, folderId));
        for (NoteAttachmentEntity file : files) {
            file.setFolderId(null);
            attachmentMapper.updateById(file);
        }
        List<DriveFolderEntity> children = lambdaQuery()
                .eq(DriveFolderEntity::getParentId, folderId)
                .list();
        for (DriveFolderEntity child : children) {
            child.setParentId(folder.getParentId());
            updateById(child);
        }
        removeById(folder.getId());
    }

    @Override
    public DriveFolderEntity requireOwnedFolder(Long folderId, Long userId) {
        DriveFolderEntity folder = getById(folderId);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "网盘文件夹不存在");
        }
        workspaceService.requireOwnedWorkspace(folder.getWorkspaceId(), userId);
        return folder;
    }

    private DriveFolderVO toVO(DriveFolderEntity folder, Long fileCount) {
        return new DriveFolderVO(
                folder.getId(),
                folder.getWorkspaceId(),
                folder.getParentId(),
                folder.getName(),
                folder.getSortOrder(),
                fileCount
        );
    }
}
