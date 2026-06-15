package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.AttachmentAccessTokenService;
import com.noto.zhihui.security.ShareLinkAccessChecker;
import com.noto.zhihui.config.NotoMinioProperties;
import com.noto.zhihui.entity.DriveFolderEntity;
import com.noto.zhihui.entity.NoteAttachmentEntity;
import com.noto.zhihui.entity.NoteAttachmentLinkEntity;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.mapper.NoteAttachmentLinkMapper;
import com.noto.zhihui.mapper.NoteAttachmentMapper;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.DriveFolderService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.ObjectStorageService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.attachment.AttachmentLinkedNoteVO;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/bmp", "image/tiff", "image/x-icon", "image/heic", "image/heif"
    );
    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml"
    );
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.ms-officetheme",
            "application/rtf",
            "application/json",
            "application/xml",
            "application/octet-stream",
            "application/epub+zip",
            "application/x-mobipocket-ebook"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff", "ico", "heic", "heif",
            "pdf", "txt", "md", "markdown", "csv", "json", "xml", "yaml", "yml", "log",
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp",
            "wps", "wpt", "et", "ett", "dps", "dpt",
            "mp4", "mov", "avi", "mkv", "webm", "wmv", "flv", "m4v",
            "mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "amr",
            "drawio", "xmind", "epub", "mobi",
            "py", "java", "js", "ts", "css", "vue", "sql"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "dll", "vbs", "ps1", "scr", "pif", "app", "jar", "sh", "bash",
            "html", "htm", "svg", "xhtml"
    );
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long MAX_BATCH_TOTAL_BYTES = 200L * 1024 * 1024;

    private final NoteAttachmentMapper attachmentMapper;
    private final NoteAttachmentLinkMapper attachmentLinkMapper;
    private final NoteService noteService;
    private final DriveFolderService driveFolderService;
    private final WorkspaceService workspaceService;
    private final ObjectStorageService objectStorageService;
    private final AttachmentAccessTokenService accessTokenService;
    private final ShareLinkAccessChecker shareLinkAccessChecker;
    private final NotoMinioProperties minioProperties;

    public AttachmentServiceImpl(
            NoteAttachmentMapper attachmentMapper,
            NoteAttachmentLinkMapper attachmentLinkMapper,
            NoteService noteService,
            DriveFolderService driveFolderService,
            WorkspaceService workspaceService,
            ObjectStorageService objectStorageService,
            AttachmentAccessTokenService accessTokenService,
            ShareLinkAccessChecker shareLinkAccessChecker,
            NotoMinioProperties minioProperties
    ) {
        this.attachmentMapper = attachmentMapper;
        this.attachmentLinkMapper = attachmentLinkMapper;
        this.noteService = noteService;
        this.driveFolderService = driveFolderService;
        this.workspaceService = workspaceService;
        this.objectStorageService = objectStorageService;
        this.accessTokenService = accessTokenService;
        this.shareLinkAccessChecker = shareLinkAccessChecker;
        this.minioProperties = minioProperties;
    }

    @Override
    public boolean isStorageAvailable() {
        return objectStorageService.isAvailable();
    }

    @Override
    @Transactional
    public AttachmentVO upload(Long noteId, MultipartFile file, Long userId) {
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        NoteAttachmentEntity entity = persistUpload(
                note.getWorkspaceId(),
                userId,
                note.getId(),
                null,
                file,
                buildNoteStorageKey(userId, note.getId(), sanitizeFileName(file.getOriginalFilename()))
        );
        createLink(entity.getId(), note.getId());
        return toVO(entity, loadLinkedNotes(List.of(entity.getId())).getOrDefault(entity.getId(), List.of()));
    }

    @Override
    @Transactional
    public AttachmentVO uploadToDrive(Long workspaceId, Long folderId, MultipartFile file, Long userId) {
        workspaceService.requireOwnedWorkspace(workspaceId, userId);
        if (folderId != null) {
            DriveFolderEntity folder = driveFolderService.requireOwnedFolder(folderId, userId);
            if (!workspaceId.equals(folder.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件夹与知识库不匹配");
            }
        }
        NoteAttachmentEntity entity = persistUpload(
                workspaceId,
                userId,
                null,
                folderId,
                file,
                buildDriveStorageKey(userId, workspaceId, sanitizeFileName(file.getOriginalFilename()))
        );
        return toVO(entity, List.of());
    }

    @Override
    public List<AttachmentVO> listByWorkspace(
            Long workspaceId,
            Long folderId,
            Boolean uncategorized,
            Long noteId,
            Boolean unlinkedOnly,
            String keyword,
            Long userId
    ) {
        workspaceService.requireOwnedWorkspace(workspaceId, userId);
        if (noteId != null) {
            noteService.requireOwnedNote(noteId, userId);
        }

        LambdaQueryWrapper<NoteAttachmentEntity> query = new LambdaQueryWrapper<NoteAttachmentEntity>()
                .eq(NoteAttachmentEntity::getWorkspaceId, workspaceId)
                .orderByDesc(NoteAttachmentEntity::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            query.like(NoteAttachmentEntity::getFileName, keyword.trim());
        } else if (Boolean.TRUE.equals(uncategorized)) {
            query.isNull(NoteAttachmentEntity::getFolderId);
        } else if (folderId != null) {
            query.eq(NoteAttachmentEntity::getFolderId, folderId);
        }

        List<NoteAttachmentEntity> entities = attachmentMapper.selectList(query);
        if (noteId != null) {
            Set<Long> linkedIds = loadAttachmentIdsForNote(noteId);
            entities = entities.stream().filter(entity -> linkedIds.contains(entity.getId())).toList();
        }
        if (Boolean.TRUE.equals(unlinkedOnly)) {
            Set<Long> linkedAny = loadAttachmentIdsWithAnyLink(
                    entities.stream().map(NoteAttachmentEntity::getId).toList()
            );
            entities = entities.stream().filter(entity -> !linkedAny.contains(entity.getId())).toList();
        }
        return toVOList(entities);
    }

    @Override
    @Transactional
    public AttachmentVO moveToFolder(Long attachmentId, Long folderId, Long userId) {
        NoteAttachmentEntity entity = requireOwnedAttachment(attachmentId, userId);
        if (folderId != null) {
            DriveFolderEntity folder = driveFolderService.requireOwnedFolder(folderId, userId);
            if (!Objects.equals(folder.getWorkspaceId(), entity.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件夹与文件不在同一知识库");
            }
            entity.setFolderId(folderId);
        } else {
            entity.setFolderId(null);
        }
        attachmentMapper.updateById(entity);
        List<AttachmentLinkedNoteVO> linkedNotes = loadLinkedNotes(List.of(attachmentId))
                .getOrDefault(attachmentId, List.of());
        return toVO(entity, linkedNotes);
    }

    @Override
    public List<AttachmentVO> listByNote(Long noteId, Long userId) {
        noteService.requireOwnedNote(noteId, userId);
        Set<Long> attachmentIds = new HashSet<>();

        attachmentLinkMapper.selectList(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                        .eq(NoteAttachmentLinkEntity::getNoteId, noteId))
                .forEach(link -> {
                    if (link.getAttachmentId() != null) {
                        attachmentIds.add(link.getAttachmentId());
                    }
                });

        attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                        .eq(NoteAttachmentEntity::getNoteId, noteId))
                .forEach(entity -> {
                    if (entity.getId() != null) {
                        attachmentIds.add(entity.getId());
                    }
                });

        if (attachmentIds.isEmpty()) {
            return List.of();
        }

        List<NoteAttachmentEntity> entities = attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                .in(NoteAttachmentEntity::getId, attachmentIds)
                .orderByDesc(NoteAttachmentEntity::getCreatedAt));
        return toVOList(entities);
    }

    @Override
    @Transactional
    public AttachmentVO linkToNote(Long attachmentId, Long noteId, Long userId) {
        NoteAttachmentEntity entity = requireOwnedAttachment(attachmentId, userId);
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        if (!Objects.equals(note.getWorkspaceId(), entity.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件与文档不在同一知识库");
        }
        createLink(attachmentId, noteId);
        if (entity.getNoteId() == null) {
            entity.setNoteId(noteId);
            attachmentMapper.updateById(entity);
        }
        List<AttachmentLinkedNoteVO> linkedNotes = loadLinkedNotes(List.of(attachmentId))
                .getOrDefault(attachmentId, List.of());
        return toVO(entity, linkedNotes);
    }

    @Override
    @Transactional
    public void unlinkFromNote(Long attachmentId, Long noteId, Long userId) {
        NoteAttachmentEntity entity = requireOwnedAttachment(attachmentId, userId);
        noteService.requireOwnedNote(noteId, userId);
        attachmentLinkMapper.delete(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                .eq(NoteAttachmentLinkEntity::getAttachmentId, attachmentId)
                .eq(NoteAttachmentLinkEntity::getNoteId, noteId));
        if (noteId.equals(entity.getNoteId())) {
            NoteAttachmentLinkEntity fallback = attachmentLinkMapper.selectOne(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                    .eq(NoteAttachmentLinkEntity::getAttachmentId, attachmentId)
                    .orderByDesc(NoteAttachmentLinkEntity::getCreatedAt)
                    .last("LIMIT 1"));
            entity.setNoteId(fallback != null ? fallback.getNoteId() : null);
            attachmentMapper.updateById(entity);
        }
    }

    @Override
    @Transactional
    public void delete(Long attachmentId, Long userId) {
        NoteAttachmentEntity entity = requireOwnedAttachment(attachmentId, userId);
        if (objectStorageService.isAvailable() && StringUtils.hasText(entity.getStorageKey())) {
            objectStorageService.delete(entity.getStorageKey());
        }
        attachmentLinkMapper.delete(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                .eq(NoteAttachmentLinkEntity::getAttachmentId, attachmentId));
        attachmentMapper.deleteById(entity.getId());
    }

    @Override
    public AttachmentVO requireAttachment(Long attachmentId) {
        NoteAttachmentEntity entity = attachmentMapper.selectById(attachmentId);
        if (entity == null) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        List<AttachmentLinkedNoteVO> linkedNotes = loadLinkedNotes(List.of(attachmentId))
                .getOrDefault(attachmentId, List.of());
        return toVO(entity, linkedNotes);
    }

    @Override
    public InputStream openFileStream(Long attachmentId, String accessToken, String shareToken) {
        AttachmentAccessTokenService.TokenClaims claims = accessTokenService.validate(
                attachmentId,
                accessToken,
                shareToken
        );
        if (claims == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (claims.shareScoped()) {
            shareLinkAccessChecker.requireShareFileAccess(claims.shareToken(), attachmentId);
        }
        NoteAttachmentEntity entity = attachmentMapper.selectById(attachmentId);
        if (entity == null) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return objectStorageService.download(entity.getStorageKey());
    }

    @Override
    public String buildShareFileUrl(Long attachmentId, String shareLinkToken) {
        String accessToken = accessTokenService.generateForShare(attachmentId, shareLinkToken);
        return buildFileUrl(attachmentId, accessToken, shareLinkToken);
    }

    @Override
    public List<NoteAttachmentEntity> resolveBatchDownloadEntities(List<Long> attachmentIds, Long userId) {
        if (!objectStorageService.isAvailable()) {
            throw new BizException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择要下载的文件");
        }
        List<Long> distinctIds = attachmentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择要下载的文件");
        }
        if (distinctIds.size() > 50) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "单次最多下载 50 个文件");
        }

        List<NoteAttachmentEntity> entities = new ArrayList<>();
        long totalBytes = 0;
        for (Long attachmentId : distinctIds) {
            NoteAttachmentEntity entity = requireOwnedAttachment(attachmentId, userId);
            entities.add(entity);
            totalBytes += entity.getFileSize() != null ? entity.getFileSize() : 0;
        }
        if (totalBytes > MAX_BATCH_TOTAL_BYTES) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批量下载总大小不能超过 200MB");
        }
        return entities;
    }

    @Override
    public List<NoteAttachmentEntity> resolveBatchDownloadSelection(
            List<Long> attachmentIds,
            List<Long> folderIds,
            Boolean uncategorized,
            Boolean unlinkedOnly,
            Long workspaceId,
            Long userId
    ) {
        if (!objectStorageService.isAvailable()) {
            throw new BizException(ErrorCode.STORAGE_UNAVAILABLE);
        }

        LinkedHashSet<Long> mergedIds = new LinkedHashSet<>();
        if (attachmentIds != null) {
            attachmentIds.stream().filter(Objects::nonNull).forEach(mergedIds::add);
        }

        boolean needsWorkspace = (folderIds != null && !folderIds.isEmpty())
                || Boolean.TRUE.equals(uncategorized)
                || Boolean.TRUE.equals(unlinkedOnly);
        if (needsWorkspace) {
            if (workspaceId == null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择知识库");
            }
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }

        if (folderIds != null && !folderIds.isEmpty()) {
            Set<Long> scopedFolderIds = collectFolderScopeIds(folderIds, workspaceId);
            if (!scopedFolderIds.isEmpty()) {
                attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                                .eq(NoteAttachmentEntity::getWorkspaceId, workspaceId)
                                .in(NoteAttachmentEntity::getFolderId, scopedFolderIds))
                        .forEach(entity -> mergedIds.add(entity.getId()));
            }
        }

        if (Boolean.TRUE.equals(uncategorized)) {
            attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                            .eq(NoteAttachmentEntity::getWorkspaceId, workspaceId)
                            .isNull(NoteAttachmentEntity::getFolderId))
                    .forEach(entity -> mergedIds.add(entity.getId()));
        }

        if (Boolean.TRUE.equals(unlinkedOnly)) {
            List<NoteAttachmentEntity> unlinkedCandidates = attachmentMapper.selectList(
                    new LambdaQueryWrapper<NoteAttachmentEntity>()
                            .eq(NoteAttachmentEntity::getWorkspaceId, workspaceId)
            );
            Set<Long> linkedAny = loadAttachmentIdsWithAnyLink(
                    unlinkedCandidates.stream().map(NoteAttachmentEntity::getId).toList()
            );
            unlinkedCandidates.stream()
                    .filter(entity -> !linkedAny.contains(entity.getId()))
                    .forEach(entity -> mergedIds.add(entity.getId()));
        }

        return resolveBatchDownloadEntities(new ArrayList<>(mergedIds), userId);
    }

    private Set<Long> collectFolderScopeIds(List<Long> rootFolderIds, Long workspaceId) {
        List<DriveFolderEntity> allFolders = driveFolderService.lambdaQuery()
                .eq(DriveFolderEntity::getWorkspaceId, workspaceId)
                .list();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (DriveFolderEntity folder : allFolders) {
            Long parentKey = folder.getParentId() != null ? folder.getParentId() : 0L;
            childrenByParent.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(folder.getId());
        }

        LinkedHashSet<Long> scoped = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Long rootId : rootFolderIds) {
            if (rootId != null) {
                queue.add(rootId);
            }
        }
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!scoped.add(current)) {
                continue;
            }
            for (Long childId : childrenByParent.getOrDefault(current, List.of())) {
                queue.add(childId);
            }
        }
        return scoped;
    }

    private static final String ZIP_DIR_UNCATEGORIZED = "未分类";
    private static final String ZIP_DIR_UNLINKED = "未关联文档";
    private static final String ZIP_DIR_LOOSE = "文件";

    @Override
    public Path buildBatchDownloadZipFile(
            List<NoteAttachmentEntity> entities,
            BatchDownloadZipContext context
    ) throws IOException {
        Path tempFile = Files.createTempFile("noto-drive-", ".zip");
        try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
            writeBatchDownloadZip(entities, context, outputStream);
            return tempFile;
        } catch (RuntimeException | IOException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }
    }

    @Override
    public void writeBatchDownloadZip(
            List<NoteAttachmentEntity> entities,
            BatchDownloadZipContext context,
            OutputStream outputStream
    ) throws IOException {
        Map<Long, DriveFolderEntity> folderById = loadWorkspaceFoldersForZip(context);
        Set<Long> selectedFolderIds = context != null && context.selectedFolderIds() != null
                ? new HashSet<>(context.selectedFolderIds())
                : Set.of();
        Set<Long> explicitIds = context != null && context.explicitAttachmentIds() != null
                ? context.explicitAttachmentIds()
                : Set.of();
        boolean uncategorizedIncluded = context != null && context.uncategorizedIncluded();
        boolean unlinkedOnlyIncluded = context != null && context.unlinkedOnlyIncluded();

        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            if (context != null && context.workspaceId() != null && !selectedFolderIds.isEmpty()) {
                writeSelectedFolderDirectoryEntries(zipOutputStream, context.workspaceId(), selectedFolderIds, folderById, usedNames);
            }
            for (NoteAttachmentEntity entity : entities) {
                String relativeDir = resolveZipDirectoryForEntity(
                        entity,
                        folderById,
                        selectedFolderIds,
                        explicitIds,
                        uncategorizedIncluded,
                        unlinkedOnlyIncluded
                );
                String fileName = sanitizeFileName(entity.getFileName());
                String entryPath = StringUtils.hasText(relativeDir)
                        ? relativeDir + "/" + fileName
                        : fileName;
                String entryName = uniqueZipEntryName(entryPath, usedNames);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                try (InputStream inputStream = objectStorageService.download(entity.getStorageKey())) {
                    inputStream.transferTo(zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
        }
    }

    /** 为选中文件夹及其子目录写入空目录项（便于解压后看到完整层级） */
    private void writeSelectedFolderDirectoryEntries(
            ZipOutputStream zipOutputStream,
            Long workspaceId,
            Set<Long> selectedFolderIds,
            Map<Long, DriveFolderEntity> folderById,
            Set<String> usedNames
    ) throws IOException {
        for (Long selectedRootId : selectedFolderIds) {
            if (selectedRootId == null || !folderById.containsKey(selectedRootId)) {
                continue;
            }
            Set<Long> subtree = collectFolderScopeIds(List.of(selectedRootId), workspaceId);
            for (Long folderId : subtree) {
                String dirPath = zipPathRelativeToSelectedFolder(selectedRootId, folderId, folderById);
                if (!StringUtils.hasText(dirPath)) {
                    continue;
                }
                String entryName = uniqueZipEntryName(dirPath + "/", usedNames);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                zipOutputStream.closeEntry();
            }
        }
    }

    private Map<Long, DriveFolderEntity> loadWorkspaceFoldersForZip(BatchDownloadZipContext context) {
        if (context == null || context.workspaceId() == null) {
            return Map.of();
        }
        return driveFolderService.lambdaQuery()
                .eq(DriveFolderEntity::getWorkspaceId, context.workspaceId())
                .list()
                .stream()
                .collect(Collectors.toMap(DriveFolderEntity::getId, f -> f, (a, b) -> a));
    }

    private String resolveZipDirectoryForEntity(
            NoteAttachmentEntity entity,
            Map<Long, DriveFolderEntity> folderById,
            Set<Long> selectedFolderIds,
            Set<Long> explicitIds,
            boolean uncategorizedIncluded,
            boolean unlinkedOnlyIncluded
    ) {
        Long folderId = entity.getFolderId();
        if (folderId != null && folderById.containsKey(folderId)) {
            if (!selectedFolderIds.isEmpty()) {
                Long anchor = findDeepestSelectedAncestor(folderId, selectedFolderIds, folderById);
                if (anchor != null) {
                    return zipPathFromFolderAnchor(anchor, folderId, folderById);
                }
            }
            String full = folderPathFromRoot(folderId, folderById);
            if (StringUtils.hasText(full)) {
                return full;
            }
        }
        if (folderId == null) {
            if (uncategorizedIncluded) {
                return ZIP_DIR_UNCATEGORIZED;
            }
            if (unlinkedOnlyIncluded && explicitIds.contains(entity.getId())) {
                return ZIP_DIR_UNLINKED;
            }
        }
        if (explicitIds.contains(entity.getId()) && folderId == null) {
            return ZIP_DIR_LOOSE;
        }
        return "";
    }

    private Long findDeepestSelectedAncestor(
            Long fileFolderId,
            Set<Long> selectedFolderIds,
            Map<Long, DriveFolderEntity> folderById
    ) {
        Long best = null;
        int bestDepth = -1;
        for (Long selectedId : selectedFolderIds) {
            if (!isFolderAncestorOrSelf(selectedId, fileFolderId, folderById)) {
                continue;
            }
            int depth = folderDepth(selectedId, folderById);
            if (depth > bestDepth) {
                bestDepth = depth;
                best = selectedId;
            }
        }
        return best;
    }

    private boolean isFolderAncestorOrSelf(
            Long ancestorId,
            Long folderId,
            Map<Long, DriveFolderEntity> folderById
    ) {
        Long current = folderId;
        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            DriveFolderEntity folder = folderById.get(current);
            if (folder == null) {
                break;
            }
            current = folder.getParentId();
        }
        return false;
    }

    private int folderDepth(Long folderId, Map<Long, DriveFolderEntity> folderById) {
        int depth = 0;
        Long current = folderId;
        while (current != null) {
            depth++;
            DriveFolderEntity folder = folderById.get(current);
            if (folder == null) {
                break;
            }
            current = folder.getParentId();
        }
        return depth;
    }

    /**
     * 勾选文件夹批量下载时：ZIP 内以所选文件夹名为根（如 111/子目录/文件），
     * 而不是从知识库根开始的完整路径。
     */
    private String zipPathRelativeToSelectedFolder(
            Long selectedRootId,
            Long targetFolderId,
            Map<Long, DriveFolderEntity> folderById
    ) {
        if (selectedRootId == null || targetFolderId == null) {
            return "";
        }
        if (!isFolderAncestorOrSelf(selectedRootId, targetFolderId, folderById)) {
            return "";
        }
        DriveFolderEntity root = folderById.get(selectedRootId);
        if (root == null) {
            return "";
        }
        String rootName = sanitizeZipPathSegment(root.getName());
        if (Objects.equals(selectedRootId, targetFolderId)) {
            return rootName;
        }
        String full = folderPathFromRoot(targetFolderId, folderById);
        String anchorFull = folderPathFromRoot(selectedRootId, folderById);
        if (!StringUtils.hasText(full) || !StringUtils.hasText(anchorFull)) {
            return rootName;
        }
        if (!full.equals(anchorFull) && !full.startsWith(anchorFull + "/")) {
            return rootName;
        }
        String suffix = full.equals(anchorFull) ? "" : full.substring(anchorFull.length() + 1);
        return StringUtils.hasText(suffix) ? rootName + "/" + suffix : rootName;
    }

    private String zipPathFromFolderAnchor(
            Long anchorFolderId,
            Long fileFolderId,
            Map<Long, DriveFolderEntity> folderById
    ) {
        return zipPathRelativeToSelectedFolder(anchorFolderId, fileFolderId, folderById);
    }

    private String folderPathFromRoot(Long folderId, Map<Long, DriveFolderEntity> folderById) {
        List<String> segments = new ArrayList<>();
        Long current = folderId;
        while (current != null) {
            DriveFolderEntity folder = folderById.get(current);
            if (folder == null) {
                break;
            }
            segments.add(sanitizeZipPathSegment(folder.getName()));
            current = folder.getParentId();
        }
        if (segments.isEmpty()) {
            return "";
        }
        java.util.Collections.reverse(segments);
        return String.join("/", segments);
    }

    private String sanitizeZipPathSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "folder";
        }
        String trimmed = raw.trim().replace('\\', '_').replace('/', '_');
        trimmed = trimmed.replaceAll("[\\x00-\\x1f]", "");
        if (trimmed.isEmpty() || ".".equals(trimmed) || "..".equals(trimmed)) {
            return "folder";
        }
        return trimmed;
    }

    /** 保留 ZIP 内路径层级；不可对整段路径使用 {@link #sanitizeFileName}（会截掉目录前缀） */
    private String sanitizeZipEntryPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "file.bin";
        }
        boolean trailingSlash = rawPath.endsWith("/");
        String normalized = rawPath.replace('\\', '/').replaceAll("/+", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 0) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return trailingSlash ? "folder/" : "file.bin";
        }
        String[] parts = normalized.split("/");
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!StringUtils.hasText(part)) {
                continue;
            }
            boolean isLastFile = i == parts.length - 1 && !trailingSlash;
            segments.add(isLastFile ? sanitizeFileName(part) : sanitizeZipPathSegment(part));
        }
        if (segments.isEmpty()) {
            return trailingSlash ? "folder/" : "file.bin";
        }
        String joined = String.join("/", segments);
        return trailingSlash ? joined + "/" : joined;
    }

    private String uniqueZipEntryName(String rawName, Set<String> usedNames) {
        String baseName = sanitizeZipEntryPath(rawName);
        if (!usedNames.contains(baseName)) {
            usedNames.add(baseName);
            return baseName;
        }
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";
        int index = 1;
        String candidate;
        do {
            candidate = stem + " (" + index + ")" + ext;
            index++;
        } while (usedNames.contains(candidate));
        usedNames.add(candidate);
        return candidate;
    }

    private Set<Long> loadAttachmentIdsForNote(Long noteId) {
        Set<Long> attachmentIds = new HashSet<>();
        attachmentLinkMapper.selectList(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                        .eq(NoteAttachmentLinkEntity::getNoteId, noteId))
                .forEach(link -> {
                    if (link.getAttachmentId() != null) {
                        attachmentIds.add(link.getAttachmentId());
                    }
                });
        attachmentMapper.selectList(new LambdaQueryWrapper<NoteAttachmentEntity>()
                        .eq(NoteAttachmentEntity::getNoteId, noteId))
                .forEach(entity -> {
                    if (entity.getId() != null) {
                        attachmentIds.add(entity.getId());
                    }
                });
        return attachmentIds;
    }

    private Set<Long> loadAttachmentIdsWithAnyLink(List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return Set.of();
        }
        return attachmentLinkMapper.selectList(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                        .in(NoteAttachmentLinkEntity::getAttachmentId, attachmentIds))
                .stream()
                .map(NoteAttachmentLinkEntity::getAttachmentId)
                .collect(Collectors.toSet());
    }

    private NoteAttachmentEntity persistUpload(
            Long workspaceId,
            Long userId,
            Long noteId,
            Long folderId,
            MultipartFile file,
            String storageKey
    ) {
        if (!objectStorageService.isAvailable()) {
            throw new BizException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择要上传的文件");
        }
        if (file.getSize() > minioProperties.getMaxFileSizeBytes()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE.getCode(), minioProperties.fileTooLargeMessage());
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        validateFileType(file.getContentType(), originalName);

        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    storageKey,
                    inputStream,
                    file.getSize(),
                    resolveContentType(file.getContentType(), originalName)
            );
        } catch (IOException ex) {
            throw new BizException(ErrorCode.EXTERNAL_ERROR.getCode(), "读取上传文件失败");
        }

        NoteAttachmentEntity entity = new NoteAttachmentEntity();
        entity.setId(IdWorker.getId());
        entity.setWorkspaceId(workspaceId);
        entity.setUploadedBy(userId);
        entity.setNoteId(noteId);
        entity.setFolderId(folderId);
        entity.setFileName(originalName);
        entity.setFileType(resolveContentType(file.getContentType(), originalName));
        entity.setFileSize(file.getSize());
        entity.setStorageKey(storageKey);
        String accessToken = accessTokenService.generate(entity.getId());
        entity.setFileUrl(buildFileUrl(entity.getId(), accessToken));
        attachmentMapper.insert(entity);
        return entity;
    }

    private void createLink(Long attachmentId, Long noteId) {
        boolean exists = attachmentLinkMapper.exists(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                .eq(NoteAttachmentLinkEntity::getAttachmentId, attachmentId)
                .eq(NoteAttachmentLinkEntity::getNoteId, noteId));
        if (exists) {
            return;
        }
        NoteAttachmentLinkEntity link = new NoteAttachmentLinkEntity();
        link.setId(IdWorker.getId());
        link.setAttachmentId(attachmentId);
        link.setNoteId(noteId);
        attachmentLinkMapper.insert(link);
    }

    @Override
    public NoteAttachmentEntity requireOwnedAttachment(Long attachmentId, Long userId) {
        return requireOwnedAttachmentInternal(attachmentId, userId);
    }

    private NoteAttachmentEntity requireOwnedAttachmentInternal(Long attachmentId, Long userId) {
        NoteAttachmentEntity entity = attachmentMapper.selectById(attachmentId);
        if (entity == null) {
            throw new BizException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (entity.getWorkspaceId() != null) {
            workspaceService.requireOwnedWorkspace(entity.getWorkspaceId(), userId);
            return entity;
        }
        if (entity.getNoteId() != null) {
            noteService.requireOwnedNote(entity.getNoteId(), userId);
            return entity;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private List<AttachmentVO> toVOList(List<NoteAttachmentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> attachmentIds = entities.stream().map(NoteAttachmentEntity::getId).toList();
        Map<Long, List<AttachmentLinkedNoteVO>> linkedNotesMap = loadLinkedNotes(attachmentIds);
        Map<Long, String> folderNames = loadFolderNames(entities);
        return entities.stream()
                .map(entity -> toVO(
                        entity,
                        linkedNotesMap.getOrDefault(entity.getId(), List.of()),
                        resolveFolderName(folderNames, entity.getFolderId())
                ))
                .toList();
    }

    private String resolveFolderName(Map<Long, String> folderNames, Long folderId) {
        if (folderId == null || folderNames.isEmpty()) {
            return null;
        }
        return folderNames.get(folderId);
    }

    private Map<Long, String> loadFolderNames(List<NoteAttachmentEntity> entities) {
        Set<Long> folderIds = entities.stream()
                .map(NoteAttachmentEntity::getFolderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (folderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (DriveFolderEntity folder : driveFolderService.listByIds(folderIds)) {
            if (folder.getId() != null) {
                names.put(folder.getId(), folder.getName() != null ? folder.getName() : "");
            }
        }
        return names;
    }

    private Map<Long, List<AttachmentLinkedNoteVO>> loadLinkedNotes(List<Long> attachmentIds) {
        if (attachmentIds.isEmpty()) {
            return Map.of();
        }
        List<NoteAttachmentLinkEntity> links = attachmentLinkMapper.selectList(new LambdaQueryWrapper<NoteAttachmentLinkEntity>()
                .in(NoteAttachmentLinkEntity::getAttachmentId, attachmentIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> noteIds = links.stream()
                .map(NoteAttachmentLinkEntity::getNoteId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, NoteEntity> noteMap = new HashMap<>();
        for (NoteEntity note : noteService.listByIds(noteIds)) {
            if (note != null && note.getId() != null) {
                noteMap.putIfAbsent(note.getId(), note);
            }
        }

        Map<Long, List<AttachmentLinkedNoteVO>> result = new HashMap<>();
        for (NoteAttachmentLinkEntity link : links) {
            Long attachmentId = link.getAttachmentId();
            Long linkedNoteId = link.getNoteId();
            if (attachmentId == null || linkedNoteId == null) {
                continue;
            }
            NoteEntity note = noteMap.get(linkedNoteId);
            if (note == null) {
                continue;
            }
            result.computeIfAbsent(attachmentId, key -> new ArrayList<>())
                    .add(new AttachmentLinkedNoteVO(note.getId(), note.getTitle()));
        }
        return result;
    }

    private String buildNoteStorageKey(Long userId, Long noteId, String fileName) {
        return "users/%d/notes/%d/%s-%s".formatted(userId, noteId, UUID.randomUUID(), fileName);
    }

    private String buildDriveStorageKey(Long userId, Long workspaceId, String fileName) {
        return "users/%d/drive/%d/%s-%s".formatted(userId, workspaceId, UUID.randomUUID(), fileName);
    }

    private String buildFileUrl(Long attachmentId, String accessToken) {
        return buildFileUrl(attachmentId, accessToken, null);
    }

    private String buildFileUrl(Long attachmentId, String accessToken, String shareToken) {
        if (StringUtils.hasText(shareToken)) {
            return "/api/v1/attachments/%d/file?access=%s&share=%s".formatted(
                    attachmentId, accessToken, shareToken
            );
        }
        return "/api/v1/attachments/%d/file?access=%s".formatted(attachmentId, accessToken);
    }

    private String sanitizeFileName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "file.bin";
        }
        String name = raw.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (!StringUtils.hasText(name)) {
            return "file.bin";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private void validateFileType(String contentType, String fileName) {
        String extension = extensionOf(fileName);
        if (StringUtils.hasText(extension) && BLOCKED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.INVALID_FILE_TYPE.getCode(), "出于安全考虑，不支持上传该类型文件");
        }
        String mime = normalizeMime(contentType);
        if (isAllowedMime(mime, extension)) {
            return;
        }
        throw new BizException(ErrorCode.INVALID_FILE_TYPE.getCode(), buildInvalidFileTypeMessage(extension, mime));
    }

    private String normalizeMime(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private boolean isAllowedMime(String mime, String extension) {
        if (StringUtils.hasText(mime) && BLOCKED_MIME_TYPES.contains(mime)) {
            return false;
        }
        if (StringUtils.hasText(mime)) {
            if (ALLOWED_IMAGE_TYPES.contains(mime)) {
                return StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension);
            }
            if (ALLOWED_MIME_TYPES.contains(mime)) {
                return StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension);
            }
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                return StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension);
            }
            if (mime.startsWith("text/")) {
                return StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension);
            }
            if (mime.startsWith("application/vnd.openxmlformats-officedocument.")
                    || mime.startsWith("application/vnd.oasis.opendocument.")) {
                return true;
            }
        }
        return StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension);
    }

    private String buildInvalidFileTypeMessage(String extension, String mime) {
        StringBuilder message = new StringBuilder("不支持的文件类型");
        if (StringUtils.hasText(extension)) {
            message.append("（.").append(extension).append("）");
        } else if (StringUtils.hasText(mime)) {
            message.append("（").append(mime).append("）");
        }
        message.append("，请上传常见文档、图片、音视频或压缩包");
        return message.toString();
    }

    private String resolveContentType(String contentType, String fileName) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return switch (extensionOf(fileName)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "ico" -> "image/x-icon";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "pdf" -> "application/pdf";
            case "txt", "log" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "html", "htm" -> "text/html";
            case "csv" -> "text/csv";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            case "tar" -> "application/x-tar";
            case "gz", "tgz" -> "application/gzip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "rtf" -> "application/rtf";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "webm" -> "video/webm";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "aac", "m4a" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private AttachmentVO toVO(
            NoteAttachmentEntity entity,
            List<AttachmentLinkedNoteVO> linkedNotes,
            String folderName
    ) {
        String fileUrl = entity.getId() != null
                ? buildFileUrl(entity.getId(), accessTokenService.generate(entity.getId()))
                : null;
        String createdAt = entity.getCreatedAt() != null ? ISO_FORMAT.format(entity.getCreatedAt()) : null;
        return new AttachmentVO(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getNoteId(),
                entity.getFolderId(),
                folderName,
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                fileUrl,
                null,
                createdAt,
                linkedNotes
        );
    }

    private AttachmentVO toVO(NoteAttachmentEntity entity, List<AttachmentLinkedNoteVO> linkedNotes) {
        String folderName = null;
        if (entity.getFolderId() != null) {
            DriveFolderEntity folder = driveFolderService.getById(entity.getFolderId());
            if (folder != null) {
                folderName = folder.getName();
            }
        }
        return toVO(entity, linkedNotes, folderName);
    }
}
