package com.noto.zhihui.service.impl;

import com.noto.zhihui.service.NoteRagIndexService;
import com.noto.zhihui.vo.ai.NoteRagIndexStatusVO;
import com.noto.zhihui.vo.ai.NoteRagReindexVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledNoteRagIndexServiceImpl implements NoteRagIndexService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void scheduleReindexNote(Long noteId, Long userId) {
    }

    @Override
    public void deleteNoteIndex(Long noteId) {
    }

    @Override
    public NoteRagReindexVO reindexNote(Long noteId, Long userId) {
        return new NoteRagReindexVO(0, 0);
    }

    @Override
    public NoteRagReindexVO reindexWorkspace(Long userId, Long workspaceId) {
        return new NoteRagReindexVO(0, 0);
    }

    @Override
    public NoteRagIndexStatusVO indexStatus(Long userId, Long workspaceId) {
        return new NoteRagIndexStatusVO(0, 0, null, false);
    }
}
