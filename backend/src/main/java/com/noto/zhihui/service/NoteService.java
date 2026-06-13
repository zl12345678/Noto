package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteTreeMoveRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.vo.note.NoteVO;

import java.util.List;

public interface NoteService extends IService<NoteEntity> {

    Page<NoteVO> pageNotes(
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
    );

    NoteVO getNoteDetail(Long id, Long userId);

    NoteVO createNote(NoteCreateRequest request, Long userId);

    NoteVO updateNote(Long id, NoteUpdateRequest request, Long userId);

    NoteVO updateFavorite(Long id, boolean isFavorite, Long userId);

    NoteVO updateStatus(Long id, int status, Long userId);

    void deleteNote(Long id, Long userId);

    void updateSummary(Long id, Long userId, String summary);

    NoteVO moveNoteInTree(Long id, NoteTreeMoveRequest request, Long userId);

    NoteEntity requireOwnedNote(Long id, Long userId);

    List<NoteVO> listRelatedNotes(Long noteId, Long userId, int limit);
}
