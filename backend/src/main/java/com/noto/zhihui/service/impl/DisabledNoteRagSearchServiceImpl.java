package com.noto.zhihui.service.impl;

import com.noto.zhihui.service.NoteRagSearchService;
import com.noto.zhihui.service.NoteRetrievalService.RetrievedNoteChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledNoteRagSearchServiceImpl implements NoteRagSearchService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<RetrievedNoteChunk> search(Long userId, Long workspaceId, Long noteId, String question, int limit) {
        return List.of();
    }
}
