package com.noto.zhihui.service;

import com.noto.zhihui.service.NoteRetrievalService.RetrievedNoteChunk;
import com.noto.zhihui.vo.ai.NoteRagIndexStatusVO;
import com.noto.zhihui.vo.ai.NoteRagReindexVO;

import java.util.List;

public interface NoteRagSearchService {

    boolean isAvailable();

    List<RetrievedNoteChunk> search(Long userId, Long workspaceId, Long noteId, String question, int limit);
}
