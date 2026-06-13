package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.entity.NoteTagEntity;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.mapper.NoteTagMapper;
import com.noto.zhihui.service.NoteTagService;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.vo.tag.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NoteTagServiceImpl extends ServiceImpl<NoteTagMapper, NoteTagEntity> implements NoteTagService {

    private final TagService tagService;

    public NoteTagServiceImpl(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    @Transactional
    public void syncNoteTags(Long noteId, Long workspaceId, List<Long> tagIds) {
        baseMapper.physicalDeleteByNoteId(noteId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<Long> distinctTagIds = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long tagId : distinctTagIds) {
            TagEntity tag = tagService.getById(tagId);
            if (tag == null || !workspaceId.equals(tag.getWorkspaceId())) {
                throw new BizException(ErrorCode.TAG_NOT_FOUND);
            }
            NoteTagEntity link = new NoteTagEntity();
            link.setNoteId(noteId);
            link.setTagId(tagId);
            link.setCreatedAt(LocalDateTime.now());
            save(link);
        }
    }

    @Override
    public List<TagVO> listTagsByNoteId(Long noteId) {
        return listTagsByNoteIds(List.of(noteId)).getOrDefault(noteId, List.of());
    }

    @Override
    public Map<Long, List<TagVO>> listTagsByNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<NoteTagEntity> relations = lambdaQuery().in(NoteTagEntity::getNoteId, noteIds).list();
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> tagIds = relations.stream().map(NoteTagEntity::getTagId).distinct().toList();
        Map<Long, TagEntity> tagMap = tagService.listByIds(tagIds).stream()
                .collect(Collectors.toMap(TagEntity::getId, tag -> tag));
        Map<Long, List<TagVO>> result = new HashMap<>();
        for (NoteTagEntity relation : relations) {
            TagEntity tag = tagMap.get(relation.getTagId());
            if (tag == null) {
                continue;
            }
            result.computeIfAbsent(relation.getNoteId(), key -> new ArrayList<>())
                    .add(toTagVO(tag));
        }
        return result;
    }

    @Override
    public void removeByNoteId(Long noteId) {
        baseMapper.physicalDeleteByNoteId(noteId);
    }

    private TagVO toTagVO(TagEntity tag) {
        return new TagVO(tag.getId(), tag.getWorkspaceId(), tag.getName(), tag.getColor());
    }
}
