package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.dto.drive.DriveFolderCreateRequest;
import com.noto.zhihui.dto.drive.DriveFolderUpdateRequest;
import com.noto.zhihui.entity.DriveFolderEntity;
import com.noto.zhihui.vo.drive.DriveFolderVO;

import java.util.List;

public interface DriveFolderService extends IService<DriveFolderEntity> {

    List<DriveFolderVO> listFolders(Long workspaceId, Long userId, String keyword);

    DriveFolderVO createFolder(DriveFolderCreateRequest request, Long userId);

    DriveFolderVO updateFolder(Long folderId, DriveFolderUpdateRequest request, Long userId);

    void deleteFolder(Long folderId, Long userId);

    DriveFolderEntity requireOwnedFolder(Long folderId, Long userId);
}
