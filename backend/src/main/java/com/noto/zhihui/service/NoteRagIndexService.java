package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.NoteRagIndexStatusVO;
import com.noto.zhihui.vo.ai.NoteRagReindexVO;

public interface NoteRagIndexService {

    boolean isAvailable();

    void scheduleReindexNote(Long noteId, Long userId);

    void deleteNoteIndex(Long noteId);

    NoteRagReindexVO reindexNote(Long noteId, Long userId);

    NoteRagReindexVO reindexWorkspace(Long userId, Long workspaceId);

    NoteRagIndexStatusVO indexStatus(Long userId, Long workspaceId);
}
