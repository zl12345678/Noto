package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteTreeMoveRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.entity.NoteTagEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.mapper.NoteMapper;
import com.noto.zhihui.service.NoteFolderService;
import com.noto.zhihui.service.NoteRagIndexService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.tag.TagVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
@Service
public class NoteServiceImpl extends ServiceImpl<NoteMapper, NoteEntity> implements NoteService {

    private static final int LIST_CONTENT_EXCERPT = 180;

    private final WorkspaceService workspaceService;
    private final NoteFolderService noteFolderService;
    private final NoteTagService noteTagService;
    private final TagService tagService;
    private final NoteRagIndexService noteRagIndexService;

    public NoteServiceImpl(
            WorkspaceService workspaceService,
            NoteFolderService noteFolderService,
            NoteTagService noteTagService,
            TagService tagService,
            @Lazy NoteRagIndexService noteRagIndexService
    ) {
        this.workspaceService = workspaceService;
        this.noteFolderService = noteFolderService;
        this.noteTagService = noteTagService;
        this.tagService = tagService;
        this.noteRagIndexService = noteRagIndexService;
    }

    @Override
    public Page<NoteVO> pageNotes(
            Long userId,
            long page,
            long size,
            String keyword,
            Integer status,
            Boolean isFavorite,
            Long workspaceId,
            Long folderId,
            Long tagId,
            boolean listView
    ) {
        validateWorkspaceAndFolder(userId, workspaceId, folderId);
        if (tagId != null) {
            TagEntity tag = tagService.getById(tagId);
            if (tag == null) {
                throw new BizException(ErrorCode.TAG_NOT_FOUND);
            }
            workspaceService.requireOwnedWorkspace(tag.getWorkspaceId(), userId);
            if (workspaceId != null && !workspaceId.equals(tag.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            workspaceId = tag.getWorkspaceId();
        }

        LambdaQueryWrapper<NoteEntity> wrapper = new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getCreatedBy, userId)
                .eq(workspaceId != null, NoteEntity::getWorkspaceId, workspaceId)
                .eq(folderId != null, NoteEntity::getFolderId, folderId)
                .eq(status != null, NoteEntity::getStatus, status)
                .eq(isFavorite != null, NoteEntity::getIsFavorite, isFavorite)
                .orderByDesc(NoteEntity::getLastEditedAt)
                .orderByDesc(NoteEntity::getUpdatedAt);

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query
                    .like(NoteEntity::getTitle, trimmed)
                    .or()
                    .like(NoteEntity::getContent, trimmed)
                    .or()
                    .like(NoteEntity::getSummary, trimmed));
        }

        if (tagId != null) {
            List<Long> noteIds = noteTagService.lambdaQuery()
                    .eq(NoteTagEntity::getTagId, tagId)
                    .list()
                    .stream()
                    .map(NoteTagEntity::getNoteId)
                    .distinct()
                    .toList();
            if (noteIds.isEmpty()) {
                return emptyPage(page, size);
            }
            wrapper.in(NoteEntity::getId, noteIds);
        }

        Page<NoteEntity> entityPage = page(new Page<>(page, size), wrapper);
        List<Long> noteIds = entityPage.getRecords().stream().map(NoteEntity::getId).toList();
        Map<Long, List<TagVO>> tagMap = noteTagService.listTagsByNoteIds(noteIds);

        Page<NoteVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(note -> toVO(note, tagMap.getOrDefault(note.getId(), List.of()), listView, keyword))
                .toList());
        return voPage;
    }

    @Override
    public NoteVO getNoteDetail(Long id, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);
        List<TagVO> tags = noteTagService.listTagsByNoteId(note.getId());
        return toVO(note, tags, false, null);
    }

    @Override
    @Transactional
    public NoteVO createNote(NoteCreateRequest request, Long userId) {
        Long workspaceId = resolveWorkspaceId(request.getWorkspaceId(), userId);
        Long folderId = request.getFolderId();
        Long parentId = request.getParentId();

        if (parentId != null) {
            NoteEntity parent = requireOwnedNote(parentId, userId);
            if (!workspaceId.equals(parent.getWorkspaceId())) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
            if (folderId == null) {
                folderId = parent.getFolderId();
            }
        }
        validateFolder(workspaceId, folderId);
        validateParentNote(workspaceId, parentId, null);

        NoteEntity note = new NoteEntity();
        note.setWorkspaceId(workspaceId);
        note.setFolderId(folderId);
        note.setParentId(parentId);
        note.setSortOrder(0);
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setContentType(request.getContentType() == null ? "markdown" : request.getContentType());
        note.setSummary(NoteTextUtils.buildSummary(request.getContent(), 200));
        note.setStatus(0);
        note.setIsFavorite(false);
        note.setCreatedBy(userId);
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        save(note);

        noteTagService.syncNoteTags(note.getId(), workspaceId, request.getTagIds());
        noteRagIndexService.scheduleReindexNote(note.getId(), userId);
        return toVO(note, noteTagService.listTagsByNoteId(note.getId()), false, null);
    }

    @Override
    @Transactional
    public NoteVO updateNote(Long id, NoteUpdateRequest request, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);
        if (request.getFolderId() != null) {
            validateFolder(note.getWorkspaceId(), request.getFolderId());
        }
        if (request.getParentId() != null) {
            validateParentNote(note.getWorkspaceId(), request.getParentId(), id);
        }

        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setContentType(request.getContentType() == null ? "markdown" : request.getContentType());
        note.setFolderId(request.getFolderId());
        if (request.getParentId() != null) {
            note.setParentId(request.getParentId());
        }
        if (request.getStatus() != null) {
            note.setStatus(request.getStatus());
        }
        if (request.getIsFavorite() != null) {
            note.setIsFavorite(request.getIsFavorite());
        }
        note.setSummary(resolveSummary(request, note.getContent(), note.getSummary()));
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        updateById(note);

        if (request.getTagIds() != null) {
            noteTagService.syncNoteTags(note.getId(), note.getWorkspaceId(), request.getTagIds());
        }
        noteRagIndexService.scheduleReindexNote(note.getId(), userId);
        return toVO(note, noteTagService.listTagsByNoteId(note.getId()), false, null);
    }

    @Override
    @Transactional
    public NoteVO updateFavorite(Long id, boolean isFavorite, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);
        note.setIsFavorite(isFavorite);
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        updateById(note);
        return toVO(note, noteTagService.listTagsByNoteId(note.getId()), false, null);
    }

    @Override
    @Transactional
    public NoteVO updateStatus(Long id, int status, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);
        note.setStatus(status);
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        updateById(note);
        return toVO(note, noteTagService.listTagsByNoteId(note.getId()), false, null);
    }

    @Override
    @Transactional
    public void deleteNote(Long id, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);
        clearWorkspaceHomeNoteId(note);

        List<Long> descendantIds = collectDescendantNoteIds(id);
        for (Long descendantId : descendantIds) {
            noteTagService.removeByNoteId(descendantId);
            noteRagIndexService.deleteNoteIndex(descendantId);
            removeById(descendantId);
        }
        noteTagService.removeByNoteId(id);
        noteRagIndexService.deleteNoteIndex(id);
        removeById(id);
    }

    @Override
    @Transactional
    public void updateSummary(Long id, Long userId, String summary) {
        NoteEntity note = requireOwnedNote(id, userId);
        note.setSummary(summary == null ? "" : summary.trim());
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        updateById(note);
    }

    @Override
    public NoteEntity requireOwnedNote(Long id, Long userId) {
        NoteEntity note = getById(id);
        if (note == null || !userId.equals(note.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return note;
    }

    private void validateWorkspaceAndFolder(Long userId, Long workspaceId, Long folderId) {
        if (workspaceId != null) {
            workspaceService.requireOwnedWorkspace(workspaceId, userId);
        }
        if (folderId != null) {
            NoteFolderEntity folder = noteFolderService.getById(folderId);
            if (folder == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            workspaceService.requireOwnedWorkspace(folder.getWorkspaceId(), userId);
        }
    }

    private Long resolveWorkspaceId(Long workspaceId, Long userId) {
        if (workspaceId == null) {
            return workspaceService.ensureDefaultWorkspace(userId);
        }
        workspaceService.requireOwnedWorkspace(workspaceId, userId);
        return workspaceId;
    }

    private void validateFolder(Long workspaceId, Long folderId) {
        if (folderId == null) {
            return;
        }
        NoteFolderEntity folder = noteFolderService.getById(folderId);
        if (folder == null || !workspaceId.equals(folder.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateParentNote(Long workspaceId, Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        NoteEntity parent = getById(parentId);
        if (parent == null || !workspaceId.equals(parent.getWorkspaceId())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        if (selfId != null && isDescendantOf(selfId, parentId)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    /** candidateId 是否位于 ancestorId 的子树中（含自身则视为循环） */
    private boolean isDescendantOf(Long ancestorId, Long candidateId) {
        Long current = candidateId;
        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            NoteEntity node = getById(current);
            if (node == null) {
                break;
            }
            current = node.getParentId();
        }
        return false;
    }

    private List<Long> collectDescendantNoteIds(Long rootId) {
        return collectDescendantsFromDb(rootId);
    }

    private List<Long> collectDescendantsFromDb(Long rootId) {
        List<Long> result = new java.util.ArrayList<>();
        List<Long> frontier = new java.util.ArrayList<>(List.of(rootId));
        while (!frontier.isEmpty()) {
            List<Long> childIds = lambdaQuery()
                    .in(NoteEntity::getParentId, frontier)
                    .list()
                    .stream()
                    .map(NoteEntity::getId)
                    .toList();
            result.addAll(childIds);
            frontier = new java.util.ArrayList<>(childIds);
        }
        return result;
    }

    private void clearWorkspaceHomeNoteId(NoteEntity note) {
        workspaceService.lambdaUpdate()
                .eq(WorkspaceEntity::getId, note.getWorkspaceId())
                .eq(WorkspaceEntity::getHomeNoteId, note.getId())
                .set(WorkspaceEntity::getHomeNoteId, null)
                .update();
    }

    private String resolveSummary(NoteUpdateRequest request, String content, String existingSummary) {
        if (Boolean.TRUE.equals(request.getAutoSummary())) {
            return NoteTextUtils.buildSummary(content, 200);
        }
        if (request.getSummary() != null) {
            String trimmed = request.getSummary().trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        if (existingSummary != null && !existingSummary.isBlank()) {
            return existingSummary;
        }
        return NoteTextUtils.buildSummary(content, 200);
    }

    @Override
    @Transactional
    public NoteVO moveNoteInTree(Long id, NoteTreeMoveRequest request, Long userId) {
        NoteEntity note = requireOwnedNote(id, userId);

        Long workspaceId = note.getWorkspaceId();
        validateFolder(workspaceId, request.getFolderId());
        validateParentNote(workspaceId, request.getParentId(), id);

        if (request.getParentId() != null) {
            NoteEntity parent = getById(request.getParentId());
            note.setParentId(request.getParentId());
            note.setFolderId(parent.getFolderId());
        } else {
            note.setParentId(null);
            note.setFolderId(request.getFolderId());
        }

        note.setSortOrder(request.getSortOrder());
        note.setUpdatedBy(userId);
        note.setLastEditedAt(LocalDateTime.now());
        updateById(note);
        return toVO(note, noteTagService.listTagsByNoteId(note.getId()), false, null);
    }

    @Override
    public List<NoteVO> listRelatedNotes(Long noteId, Long userId, int limit) {
        NoteEntity note = requireOwnedNote(noteId, userId);
        int max = Math.min(Math.max(limit, 1), 10);
        Map<Long, Integer> scores = new LinkedHashMap<>();

        if (note.getFolderId() != null) {
            lambdaQuery()
                    .eq(NoteEntity::getCreatedBy, userId)
                    .eq(NoteEntity::getWorkspaceId, note.getWorkspaceId())
                    .eq(NoteEntity::getFolderId, note.getFolderId())
                    .ne(NoteEntity::getId, noteId)
                    .eq(NoteEntity::getStatus, 0)
                    .list()
                    .forEach(item -> scores.merge(item.getId(), 2, Integer::sum));
        }

        List<Long> tagIds = noteTagService.lambdaQuery()
                .eq(NoteTagEntity::getNoteId, noteId)
                .list()
                .stream()
                .map(NoteTagEntity::getTagId)
                .toList();

        if (!tagIds.isEmpty()) {
            noteTagService.lambdaQuery()
                    .in(NoteTagEntity::getTagId, tagIds)
                    .ne(NoteTagEntity::getNoteId, noteId)
                    .list()
                    .stream()
                    .map(NoteTagEntity::getNoteId)
                    .distinct()
                    .forEach(relatedId -> scores.merge(relatedId, 3, Integer::sum));
        }

        String titleKey = note.getTitle() == null ? "" : note.getTitle().trim();
        if (titleKey.length() >= 2) {
            String fragment = titleKey.length() > 4 ? titleKey.substring(0, 4) : titleKey;
            lambdaQuery()
                    .eq(NoteEntity::getCreatedBy, userId)
                    .eq(NoteEntity::getWorkspaceId, note.getWorkspaceId())
                    .ne(NoteEntity::getId, noteId)
                    .eq(NoteEntity::getStatus, 0)
                    .like(NoteEntity::getTitle, fragment)
                    .list()
                    .forEach(item -> scores.merge(item.getId(), 1, Integer::sum));
        }

        if (scores.isEmpty()) {
            return List.of();
        }

        List<Long> sortedIds = scores.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(max)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, NoteEntity> noteMap = listByIds(sortedIds).stream()
                .collect(java.util.stream.Collectors.toMap(NoteEntity::getId, item -> item, (a, b) -> a));

        return sortedIds.stream()
                .map(noteMap::get)
                .filter(Objects::nonNull)
                .map(item -> toVO(item, noteTagService.listTagsByNoteId(item.getId()), true, null))
                .toList();
    }

    private Page<NoteVO> emptyPage(long page, long size) {
        Page<NoteVO> voPage = new Page<>(page, size, 0);
        voPage.setRecords(List.of());
        return voPage;
    }

    private NoteVO toVO(NoteEntity note, List<TagVO> tags, boolean listView, String keyword) {
        String content = listView ? null : note.getContent();
        String excerpt = listView
                ? NoteTextUtils.highlightSnippet(note.getContent(), keyword, LIST_CONTENT_EXCERPT)
                : null;
        return new NoteVO(
                note.getId(),
                note.getWorkspaceId(),
                note.getFolderId(),
                note.getParentId(),
                note.getSortOrder() == null ? 0 : note.getSortOrder(),
                note.getTitle(),
                content,
                excerpt,
                note.getContentType(),
                note.getSummary(),
                note.getStatus(),
                note.getIsFavorite(),
                note.getLastEditedAt(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                tags
        );
    }
}
