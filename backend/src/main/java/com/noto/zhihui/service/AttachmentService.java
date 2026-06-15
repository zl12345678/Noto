package com.noto.zhihui.service;

import com.noto.zhihui.entity.NoteAttachmentEntity;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface AttachmentService {

    boolean isStorageAvailable();

    AttachmentVO upload(Long noteId, MultipartFile file, Long userId);

    AttachmentVO uploadToDrive(Long workspaceId, Long folderId, MultipartFile file, Long userId);

    List<AttachmentVO> listByWorkspace(
            Long workspaceId,
            Long folderId,
            Boolean uncategorized,
            Long noteId,
            Boolean unlinkedOnly,
            String keyword,
            Long userId
    );

    AttachmentVO moveToFolder(Long attachmentId, Long folderId, Long userId);

    List<AttachmentVO> listByNote(Long noteId, Long userId);

    AttachmentVO linkToNote(Long attachmentId, Long noteId, Long userId);

    void unlinkFromNote(Long attachmentId, Long noteId, Long userId);

    void delete(Long attachmentId, Long userId);

    AttachmentVO requireAttachment(Long attachmentId);

    NoteAttachmentEntity requireOwnedAttachment(Long attachmentId, Long userId);

    InputStream openFileStream(Long attachmentId, String accessToken, String shareToken);

    String buildShareFileUrl(Long attachmentId, String shareLinkToken);

    List<NoteAttachmentEntity> resolveBatchDownloadEntities(List<Long> attachmentIds, Long userId);

    List<NoteAttachmentEntity> resolveBatchDownloadSelection(
            List<Long> attachmentIds,
            List<Long> folderIds,
            Boolean uncategorized,
            Boolean unlinkedOnly,
            Long workspaceId,
            Long userId
    );

    /** 批量下载打包上下文（用于 ZIP 内保留文件夹结构） */
    record BatchDownloadZipContext(
            Long workspaceId,
            List<Long> selectedFolderIds,
            boolean uncategorizedIncluded,
            boolean unlinkedOnlyIncluded,
            Set<Long> explicitAttachmentIds
    ) {}

    Path buildBatchDownloadZipFile(
            List<NoteAttachmentEntity> entities,
            BatchDownloadZipContext context
    ) throws IOException;

    void writeBatchDownloadZip(
            List<NoteAttachmentEntity> entities,
            BatchDownloadZipContext context,
            OutputStream outputStream
    ) throws IOException;
}
