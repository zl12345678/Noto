package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.common.util.NoteChunkSplitter;
import com.noto.zhihui.common.util.NoteChunkSplitter.ContentChunk;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteRagChunkEntity;
import com.noto.zhihui.mapper.NoteRagChunkMapper;
import com.noto.zhihui.service.NoteRagIndexService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.support.PgVectorSupport;
import com.noto.zhihui.vo.ai.NoteRagIndexStatusVO;
import com.noto.zhihui.vo.ai.NoteRagReindexVO;
import com.noto.zhihui.vo.note.NoteVO;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class NoteRagIndexServiceImpl implements NoteRagIndexService {

    private final NoteService noteService;
    private final NoteRagChunkMapper noteRagChunkMapper;
    private final EmbeddingModel embeddingModel;
    private final NotoAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final PgVectorSupport pgVectorSupport;

    public NoteRagIndexServiceImpl(
            NoteService noteService,
            NoteRagChunkMapper noteRagChunkMapper,
            EmbeddingModel embeddingModel,
            NotoAiProperties aiProperties,
            ObjectMapper objectMapper,
            PgVectorSupport pgVectorSupport
    ) {
        this.noteService = noteService;
        this.noteRagChunkMapper = noteRagChunkMapper;
        this.embeddingModel = embeddingModel;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.pgVectorSupport = pgVectorSupport;
    }

    @Override
    public boolean isAvailable() {
        return aiProperties.isRagEnabled() && StringUtils.hasText(aiProperties.getApiKey());
    }

    @Override
    public void scheduleReindexNote(Long noteId, Long userId) {
        if (!isAvailable() || noteId == null || userId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                reindexNote(noteId, userId);
            } catch (Exception ignored) {
                // 后台索引失败不影响保存
            }
        });
    }

    @Override
    @Transactional
    public void deleteNoteIndex(Long noteId) {
        if (noteId == null) {
            return;
        }
        noteRagChunkMapper.delete(Wrappers.<NoteRagChunkEntity>lambdaQuery()
                .eq(NoteRagChunkEntity::getNoteId, noteId));
    }

    @Override
    @Transactional
    public NoteRagReindexVO reindexNote(Long noteId, Long userId) {
        if (!isAvailable()) {
            return new NoteRagReindexVO(0, 0);
        }
        NoteEntity note = noteService.requireOwnedNote(noteId, userId);
        deleteNoteIndex(noteId);

        List<ContentChunk> chunks = NoteChunkSplitter.split(
                note.getContent(),
                aiProperties.getRagChunkSize(),
                aiProperties.getRagChunkOverlap()
        );
        if (chunks.isEmpty()) {
            return new NoteRagReindexVO(0, 0);
        }

        List<TextSegment> segments = new ArrayList<>();
        for (ContentChunk chunk : chunks) {
            String text = buildEmbeddingText(note.getTitle(), chunk.content());
            segments.add(TextSegment.from(text));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        int saved = 0;
        for (int i = 0; i < chunks.size(); i++) {
            ContentChunk chunk = chunks.get(i);
            Embedding embedding = embeddings.get(i);
            NoteRagChunkEntity entity = new NoteRagChunkEntity();
            entity.setNoteId(note.getId());
            entity.setWorkspaceId(note.getWorkspaceId());
            entity.setUserId(userId);
            entity.setChunkIndex(chunk.index());
            entity.setContent(chunk.content());
            entity.setOffsetStart(chunk.offsetStart());
            entity.setOffsetEnd(chunk.offsetEnd());
            entity.setEmbeddingModel(aiProperties.getEmbeddingModel());
            entity.setEmbedding(serializeEmbedding(embedding.vector()));
            noteRagChunkMapper.insert(entity);
            pgVectorSupport.storeEmbeddingVector(entity.getId(), embedding.vector());
            saved++;
        }
        return new NoteRagReindexVO(1, saved);
    }

    @Override
    public NoteRagReindexVO reindexWorkspace(Long userId, Long workspaceId) {
        if (!isAvailable()) {
            return new NoteRagReindexVO(0, 0);
        }
        int noteCount = 0;
        int chunkCount = 0;
        long page = 1;
        while (true) {
            Page<NoteVO> result = noteService.pageNotes(
                    userId, page, 20, null, 0, null, workspaceId, null, null, true
            );
            if (result.getRecords().isEmpty()) {
                break;
            }
            for (NoteVO note : result.getRecords()) {
                NoteRagReindexVO indexed = reindexNote(note.getId(), userId);
                if (indexed.getIndexedChunks() > 0) {
                    noteCount += indexed.getIndexedNotes();
                    chunkCount += indexed.getIndexedChunks();
                }
            }
            if (page >= result.getPages()) {
                break;
            }
            page++;
        }
        return new NoteRagReindexVO(noteCount, chunkCount);
    }

    @Override
    public NoteRagIndexStatusVO indexStatus(Long userId, Long workspaceId) {
        var wrapper = Wrappers.<NoteRagChunkEntity>lambdaQuery()
                .eq(NoteRagChunkEntity::getUserId, userId);
        if (workspaceId != null) {
            wrapper.eq(NoteRagChunkEntity::getWorkspaceId, workspaceId);
        }
        List<NoteRagChunkEntity> chunks = noteRagChunkMapper.selectList(wrapper);
        long noteCount = chunks.stream().map(NoteRagChunkEntity::getNoteId).distinct().count();
        return new NoteRagIndexStatusVO(
                noteCount,
                chunks.size(),
                aiProperties.getEmbeddingModel(),
                isAvailable()
        );
    }

    private String buildEmbeddingText(String title, String chunkContent) {
        if (StringUtils.hasText(title)) {
            return "标题：" + title.trim() + "\n" + chunkContent;
        }
        return chunkContent;
    }

    private String serializeEmbedding(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception ex) {
            throw new IllegalStateException("向量序列化失败", ex);
        }
    }
}
