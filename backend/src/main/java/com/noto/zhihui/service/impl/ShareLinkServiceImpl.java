package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.constants.ShareResourceType;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.share.ShareBatchCreateRequest;
import com.noto.zhihui.dto.share.ShareCreateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.ShareLinkEntity;
import com.noto.zhihui.entity.ShareLinkItemEntity;
import com.noto.zhihui.mapper.ShareLinkItemMapper;
import com.noto.zhihui.mapper.ShareLinkMapper;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.ShareLinkService;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import com.noto.zhihui.vo.share.ShareLinkVO;
import com.noto.zhihui.vo.share.SharedContentVO;
import com.noto.zhihui.vo.share.SharedFileItemVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ShareLinkServiceImpl extends ServiceImpl<ShareLinkMapper, ShareLinkEntity> implements ShareLinkService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_EXPIRES_DAYS = 365;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_BATCH_SIZE = 50;

    private final NoteService noteService;
    private final AttachmentService attachmentService;
    private final ShareLinkItemMapper shareLinkItemMapper;
    private final PasswordEncoder passwordEncoder;

    public ShareLinkServiceImpl(
            NoteService noteService,
            AttachmentService attachmentService,
            ShareLinkItemMapper shareLinkItemMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.noteService = noteService;
        this.attachmentService = attachmentService;
        this.shareLinkItemMapper = shareLinkItemMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<ShareLinkVO> listMyShares(Long userId) {
        List<ShareLinkEntity> links = lambdaQuery()
                .eq(ShareLinkEntity::getCreatedBy, userId)
                .eq(ShareLinkEntity::getEnabled, true)
                .orderByDesc(ShareLinkEntity::getCreatedAt)
                .list();
        return links.stream()
                .map(this::toListVO)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public ShareLinkVO getNoteShare(Long noteId, Long userId) {
        noteService.requireOwnedNote(noteId, userId);
        return toVO(findActiveLink(ShareResourceType.NOTE, noteId));
    }

    @Override
    @Transactional
    public ShareLinkVO createNoteShare(Long noteId, ShareCreateRequest request, Long userId) {
        noteService.requireOwnedNote(noteId, userId);
        return toVO(createOrRefreshShare(ShareResourceType.NOTE, noteId, userId, request));
    }

    @Override
    @Transactional
    public void revokeNoteShare(Long noteId, Long userId) {
        noteService.requireOwnedNote(noteId, userId);
        revokeShare(ShareResourceType.NOTE, noteId);
    }

    @Override
    public ShareLinkVO getAttachmentShare(Long attachmentId, Long userId) {
        attachmentService.requireOwnedAttachment(attachmentId, userId);
        return toVO(findActiveLink(ShareResourceType.ATTACHMENT, attachmentId));
    }

    @Override
    @Transactional
    public ShareLinkVO createAttachmentShare(Long attachmentId, ShareCreateRequest request, Long userId) {
        attachmentService.requireOwnedAttachment(attachmentId, userId);
        return toVO(createOrRefreshShare(ShareResourceType.ATTACHMENT, attachmentId, userId, request));
    }

    @Override
    @Transactional
    public void revokeAttachmentShare(Long attachmentId, Long userId) {
        attachmentService.requireOwnedAttachment(attachmentId, userId);
        revokeShare(ShareResourceType.ATTACHMENT, attachmentId);
    }

    @Override
    @Transactional
    public ShareLinkVO createBatchAttachmentShare(ShareBatchCreateRequest request, Long userId) {
        List<Long> ids = normalizeBatchIds(request.getIds());
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择要分享的文件");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "单次最多分享 " + MAX_BATCH_SIZE + " 个文件");
        }
        ids.forEach(id -> attachmentService.requireOwnedAttachment(id, userId));

        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setId(IdWorker.getId());
        entity.setToken(generateToken());
        entity.setResourceType(ShareResourceType.BATCH);
        entity.setResourceId(null);
        entity.setCreatedBy(userId);
        entity.setExpiresAt(resolveExpiresAt(request.getExpiresInDays()));
        entity.setEnabled(true);
        entity.setViewCount(0L);
        entity.setPasswordHash(resolvePasswordHash(request.getPassword(), null));
        save(entity);
        saveBatchItems(entity.getId(), ids);
        return toVO(entity);
    }

    @Override
    public ShareLinkVO getShareInfo(String token, Long userId) {
        ShareLinkEntity link = requireOwnedShareLink(token, userId);
        return toVO(link);
    }

    @Override
    @Transactional
    public void revokeShareByToken(String token, Long userId) {
        ShareLinkEntity link = requireOwnedShareLink(token, userId);
        link.setEnabled(false);
        updateById(link);
    }

    @Override
    public SharedContentVO getPublicContent(String token) {
        ShareLinkEntity link = requireAccessibleLink(token);
        if (isPasswordProtected(link)) {
            return buildPasswordGate(link);
        }
        return buildUnlockedContent(link, true);
    }

    @Override
    @Transactional
    public SharedContentVO unlockPublicContent(String token, String password) {
        ShareLinkEntity link = requireAccessibleLink(token);
        if (!isPasswordProtected(link)) {
            return buildUnlockedContent(link, true);
        }
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password.trim(), link.getPasswordHash())) {
            throw new BizException(ErrorCode.SHARE_PASSWORD_INVALID);
        }
        return buildUnlockedContent(link, true);
    }

    private SharedContentVO buildPasswordGate(ShareLinkEntity link) {
        SharedContentVO vo = new SharedContentVO();
        vo.setResourceType(link.getResourceType());
        vo.setPasswordRequired(true);
        vo.setTitle(resolveShareTitle(link, false));
        vo.setExpiresAt(formatTime(link.getExpiresAt()));
        vo.setSharedAt(formatTime(link.getCreatedAt()));
        return vo;
    }

    private SharedContentVO buildUnlockedContent(ShareLinkEntity link, boolean recordView) {
        if (recordView) {
            recordView(link);
        }
        if (ShareResourceType.NOTE.equals(link.getResourceType())) {
            return buildNoteContent(link);
        }
        if (ShareResourceType.ATTACHMENT.equals(link.getResourceType())) {
            return buildAttachmentContent(link);
        }
        if (ShareResourceType.BATCH.equals(link.getResourceType())) {
            return buildBatchContent(link);
        }
        throw new BizException(ErrorCode.SHARE_NOT_FOUND);
    }

    private SharedContentVO buildNoteContent(ShareLinkEntity link) {
        NoteEntity note = noteService.getById(link.getResourceId());
        if (note == null) {
            throw new BizException(ErrorCode.SHARE_NOT_FOUND);
        }
        SharedContentVO vo = new SharedContentVO();
        vo.setResourceType(ShareResourceType.NOTE);
        vo.setTitle(StringUtils.hasText(note.getTitle()) ? note.getTitle() : "未命名文档");
        vo.setContent(note.getContent() != null ? note.getContent() : "");
        vo.setContentType(StringUtils.hasText(note.getContentType()) ? note.getContentType() : "markdown");
        vo.setPasswordRequired(false);
        vo.setViewCount(link.getViewCount());
        vo.setExpiresAt(formatTime(link.getExpiresAt()));
        vo.setSharedAt(formatTime(link.getCreatedAt()));
        return vo;
    }

    private SharedContentVO buildAttachmentContent(ShareLinkEntity link) {
        AttachmentVO attachment = attachmentService.requireAttachment(link.getResourceId());
        SharedContentVO vo = new SharedContentVO();
        vo.setResourceType(ShareResourceType.ATTACHMENT);
        vo.setTitle(attachment.getFileName());
        vo.setFileName(attachment.getFileName());
        vo.setFileType(attachment.getFileType());
        vo.setFileSize(attachment.getFileSize());
        vo.setFileUrl(attachmentService.buildShareFileUrl(link.getResourceId(), link.getToken()));
        vo.setPasswordRequired(false);
        vo.setViewCount(link.getViewCount());
        vo.setExpiresAt(formatTime(link.getExpiresAt()));
        vo.setSharedAt(formatTime(link.getCreatedAt()));
        return vo;
    }

    private SharedContentVO buildBatchContent(ShareLinkEntity link) {
        List<SharedFileItemVO> files = loadBatchFiles(link);
        if (files.isEmpty()) {
            throw new BizException(ErrorCode.SHARE_NOT_FOUND);
        }
        SharedContentVO vo = new SharedContentVO();
        vo.setResourceType(ShareResourceType.BATCH);
        vo.setTitle("批量分享（" + files.size() + " 个文件）");
        vo.setFiles(files);
        vo.setPasswordRequired(false);
        vo.setViewCount(link.getViewCount());
        vo.setExpiresAt(formatTime(link.getExpiresAt()));
        vo.setSharedAt(formatTime(link.getCreatedAt()));
        return vo;
    }

    private List<SharedFileItemVO> loadBatchFiles(ShareLinkEntity link) {
        List<ShareLinkItemEntity> items = shareLinkItemMapper.selectList(new LambdaQueryWrapper<ShareLinkItemEntity>()
                .eq(ShareLinkItemEntity::getShareLinkId, link.getId())
                .orderByAsc(ShareLinkItemEntity::getSortOrder)
                .orderByAsc(ShareLinkItemEntity::getId));
        List<SharedFileItemVO> files = new ArrayList<>();
        for (ShareLinkItemEntity item : items) {
            AttachmentVO attachment = attachmentService.requireAttachment(item.getResourceId());
            SharedFileItemVO file = new SharedFileItemVO();
            file.setId(attachment.getId());
            file.setFileName(attachment.getFileName());
            file.setFileType(attachment.getFileType());
            file.setFileSize(attachment.getFileSize());
            file.setFileUrl(attachmentService.buildShareFileUrl(attachment.getId(), link.getToken()));
            files.add(file);
        }
        return files;
    }

    private void saveBatchItems(Long shareLinkId, List<Long> attachmentIds) {
        int order = 0;
        for (Long attachmentId : attachmentIds) {
            ShareLinkItemEntity item = new ShareLinkItemEntity();
            item.setId(IdWorker.getId());
            item.setShareLinkId(shareLinkId);
            item.setResourceType(ShareResourceType.ATTACHMENT);
            item.setResourceId(attachmentId);
            item.setSortOrder(order++);
            shareLinkItemMapper.insert(item);
        }
    }

    private ShareLinkEntity findActiveLink(String resourceType, Long resourceId) {
        return lambdaQuery()
                .eq(ShareLinkEntity::getResourceType, resourceType)
                .eq(ShareLinkEntity::getResourceId, resourceId)
                .eq(ShareLinkEntity::getEnabled, true)
                .orderByDesc(ShareLinkEntity::getCreatedAt)
                .last("LIMIT 1")
                .one();
    }

    private ShareLinkEntity createOrRefreshShare(
            String resourceType,
            Long resourceId,
            Long userId,
            ShareCreateRequest request
    ) {
        ShareLinkEntity existing = findActiveLink(resourceType, resourceId);
        LocalDateTime expiresAt = resolveExpiresAt(request != null ? request.getExpiresInDays() : null);
        String passwordHash = resolvePasswordHash(
                request != null ? request.getPassword() : null,
                existing != null ? existing.getPasswordHash() : null
        );
        if (existing != null && !isExpired(existing)) {
            existing.setExpiresAt(expiresAt);
            existing.setEnabled(true);
            existing.setPasswordHash(passwordHash);
            updateById(existing);
            return existing;
        }
        if (existing != null) {
            existing.setEnabled(false);
            updateById(existing);
        }
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setId(IdWorker.getId());
        entity.setToken(generateToken());
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setCreatedBy(userId);
        entity.setExpiresAt(expiresAt);
        entity.setEnabled(true);
        entity.setViewCount(0L);
        entity.setPasswordHash(passwordHash);
        save(entity);
        return entity;
    }

    private void revokeShare(String resourceType, Long resourceId) {
        ShareLinkEntity link = findActiveLink(resourceType, resourceId);
        if (link == null) {
            return;
        }
        link.setEnabled(false);
        updateById(link);
    }

    private ShareLinkEntity requireAccessibleLink(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.SHARE_NOT_FOUND);
        }
        ShareLinkEntity link = lambdaQuery()
                .eq(ShareLinkEntity::getToken, token.trim())
                .one();
        if (link == null || !Boolean.TRUE.equals(link.getEnabled())) {
            throw new BizException(ErrorCode.SHARE_NOT_FOUND);
        }
        if (isExpired(link)) {
            throw new BizException(ErrorCode.SHARE_EXPIRED);
        }
        return link;
    }

    private ShareLinkEntity requireOwnedShareLink(String token, Long userId) {
        ShareLinkEntity link = requireAccessibleLink(token);
        if (!Objects.equals(link.getCreatedBy(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return link;
    }

    private void recordView(ShareLinkEntity link) {
        link.setViewCount((link.getViewCount() == null ? 0L : link.getViewCount()) + 1);
        link.setLastViewedAt(LocalDateTime.now());
        updateById(link);
    }

    private boolean isPasswordProtected(ShareLinkEntity link) {
        return StringUtils.hasText(link.getPasswordHash());
    }

    private String resolvePasswordHash(String password, String existingHash) {
        if (password == null) {
            return existingHash;
        }
        String trimmed = password.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (trimmed.length() < MIN_PASSWORD_LENGTH || trimmed.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(
                    ErrorCode.BAD_REQUEST.getCode(),
                    "访问密码长度需在 " + MIN_PASSWORD_LENGTH + "–" + MAX_PASSWORD_LENGTH + " 位"
            );
        }
        return passwordEncoder.encode(trimmed);
    }

    private LocalDateTime resolveExpiresAt(Integer expiresInDays) {
        if (expiresInDays == null) {
            return null;
        }
        if (expiresInDays <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "分享有效期必须大于 0 天");
        }
        if (expiresInDays > MAX_EXPIRES_DAYS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "分享有效期不能超过 " + MAX_EXPIRES_DAYS + " 天");
        }
        return LocalDateTime.now().plusDays(expiresInDays);
    }

    private List<Long> normalizeBatchIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        ids.stream().filter(Objects::nonNull).forEach(unique::add);
        return List.copyOf(unique);
    }

    private boolean isExpired(ShareLinkEntity link) {
        return link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private String generateToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            boolean exists = count(new LambdaQueryWrapper<ShareLinkEntity>().eq(ShareLinkEntity::getToken, token)) > 0;
            if (!exists) {
                return token;
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "生成分享链接失败");
    }

    private ShareLinkVO toListVO(ShareLinkEntity entity) {
        ShareLinkVO vo = toVO(entity);
        if (vo == null) {
            return null;
        }
        vo.setTitle(resolveShareTitle(entity, true));
        vo.setWorkspaceId(resolveWorkspaceId(entity));
        return vo;
    }

    private Long resolveWorkspaceId(ShareLinkEntity link) {
        if (ShareResourceType.NOTE.equals(link.getResourceType()) && link.getResourceId() != null) {
            NoteEntity note = noteService.getById(link.getResourceId());
            return note != null ? note.getWorkspaceId() : null;
        }
        if (ShareResourceType.ATTACHMENT.equals(link.getResourceType()) && link.getResourceId() != null) {
            return resolveAttachmentWorkspaceId(link.getResourceId());
        }
        if (ShareResourceType.BATCH.equals(link.getResourceType())) {
            ShareLinkItemEntity first = shareLinkItemMapper.selectOne(new LambdaQueryWrapper<ShareLinkItemEntity>()
                    .eq(ShareLinkItemEntity::getShareLinkId, link.getId())
                    .orderByAsc(ShareLinkItemEntity::getSortOrder)
                    .orderByAsc(ShareLinkItemEntity::getId)
                    .last("LIMIT 1"));
            if (first != null) {
                return resolveAttachmentWorkspaceId(first.getResourceId());
            }
        }
        return null;
    }

    private Long resolveAttachmentWorkspaceId(Long attachmentId) {
        try {
            AttachmentVO attachment = attachmentService.requireAttachment(attachmentId);
            return attachment.getWorkspaceId();
        } catch (BizException ex) {
            return null;
        }
    }

    private ShareLinkVO toVO(ShareLinkEntity entity) {
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled()) || isExpired(entity)) {
            return null;
        }
        ShareLinkVO vo = new ShareLinkVO();
        vo.setToken(entity.getToken());
        vo.setSharePath("/share/" + entity.getToken());
        vo.setResourceType(entity.getResourceType());
        vo.setResourceId(entity.getResourceId());
        vo.setEnabled(entity.getEnabled());
        vo.setPasswordProtected(isPasswordProtected(entity));
        vo.setViewCount(entity.getViewCount() == null ? 0L : entity.getViewCount());
        vo.setLastViewedAt(formatTime(entity.getLastViewedAt()));
        vo.setItemCount(countShareItems(entity));
        vo.setExpiresAt(formatTime(entity.getExpiresAt()));
        vo.setCreatedAt(formatTime(entity.getCreatedAt()));
        return vo;
    }

    private int countShareItems(ShareLinkEntity entity) {
        if (!ShareResourceType.BATCH.equals(entity.getResourceType())) {
            return 1;
        }
        Long count = shareLinkItemMapper.selectCount(new LambdaQueryWrapper<ShareLinkItemEntity>()
                .eq(ShareLinkItemEntity::getShareLinkId, entity.getId()));
        return count == null ? 0 : count.intValue();
    }

    private String resolveShareTitle(ShareLinkEntity link, boolean includeContent) {
        if (ShareResourceType.NOTE.equals(link.getResourceType())) {
            if (link.getResourceId() == null) {
                return "文档已删除";
            }
            NoteEntity note = noteService.getById(link.getResourceId());
            if (note == null) {
                return "文档已删除";
            }
            if (StringUtils.hasText(note.getTitle())) {
                return note.getTitle();
            }
            return "未命名文档";
        }
        if (ShareResourceType.ATTACHMENT.equals(link.getResourceType())) {
            if (link.getResourceId() == null) {
                return "文件已删除";
            }
            try {
                AttachmentVO attachment = attachmentService.requireAttachment(link.getResourceId());
                return attachment.getFileName();
            } catch (BizException ex) {
                return "文件已删除";
            }
        }
        if (ShareResourceType.BATCH.equals(link.getResourceType())) {
            int count = countShareItems(link);
            return includeContent ? "批量分享（" + count + " 个文件）" : "批量文件分享";
        }
        return "分享内容";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(ISO);
    }
}
