package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.entity.NoteTagEntity;
import com.noto.zhihui.vo.tag.TagVO;

import java.util.List;
import java.util.Map;

public interface NoteTagService extends IService<NoteTagEntity> {

    void syncNoteTags(Long noteId, Long workspaceId, List<Long> tagIds);

    List<TagVO> listTagsByNoteId(Long noteId);

    Map<Long, List<TagVO>> listTagsByNoteIds(List<Long> noteIds);

    void removeByNoteId(Long noteId);
}
