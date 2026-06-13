package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.entity.NoteEntity;
import com.noto.zhihui.entity.NoteRagChunkEntity;
import com.noto.zhihui.mapper.NoteRagChunkMapper;
import com.noto.zhihui.service.NoteRagSearchService;
import com.noto.zhihui.service.NoteRetrievalService.RetrievedNoteChunk;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.support.PgVectorSupport;
import com.noto.zhihui.vo.rag.NoteRagVectorSearchRow;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "noto.ai", name = "enabled", havingValue = "true")
public class NoteRagSearchServiceImpl implements NoteRagSearchService {

    private static final double MIN_SIMILARITY = 0.30D;
    private static final int MAX_CHUNKS_PER_NOTE = 2;

    private final NoteRagChunkMapper noteRagChunkMapper;
    private final NoteService noteService;
    private final EmbeddingModel embeddingModel;
    private final NotoAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final PgVectorSupport pgVectorSupport;

    public NoteRagSearchServiceImpl(
            NoteRagChunkMapper noteRagChunkMapper,
            NoteService noteService,
            EmbeddingModel embeddingModel,
            NotoAiProperties aiProperties,
            ObjectMapper objectMapper,
            PgVectorSupport pgVectorSupport
    ) {
        this.noteRagChunkMapper = noteRagChunkMapper;
        this.noteService = noteService;
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
    public List<RetrievedNoteChunk> search(Long userId, Long workspaceId, Long noteId, String question, int limit) {
        if (!isAvailable() || !StringUtils.hasText(question) || userId == null) {
            return List.of();
        }
        int topK = Math.min(Math.max(limit, 1), 12);
        Embedding queryEmbedding = embeddingModel.embed(question.trim()).content();
        float[] queryVector = queryEmbedding.vector();

        List<ScoredChunk> scored = pgVectorSupport.isAvailable()
                ? searchWithPgVector(userId, workspaceId, noteId, queryVector, topK)
                : searchInMemory(userId, workspaceId, noteId, queryVector, topK);
        if (scored.isEmpty()) {
            return List.of();
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::similarity).reversed());
        Map<Long, String> titleCache = new HashMap<>();
        Map<Long, Integer> noteCount = new HashMap<>();
        List<RetrievedNoteChunk> results = new ArrayList<>();
        for (ScoredChunk item : scored) {
            NoteRagChunkEntity chunk = item.chunk();
            int used = noteCount.getOrDefault(chunk.getNoteId(), 0);
            if (used >= MAX_CHUNKS_PER_NOTE) {
                continue;
            }
            String title = titleCache.computeIfAbsent(chunk.getNoteId(), id -> loadNoteTitle(id, userId));
            String snippet = chunk.getContent() == null ? "" : chunk.getContent().trim();
            int keywordBoost = keywordOverlapScore(snippet, question);
            int score = (int) Math.round(item.similarity() * 100) + keywordBoost;
            results.add(new RetrievedNoteChunk(
                    chunk.getNoteId(),
                    title,
                    snippet,
                    pgVectorSupport.isAvailable() ? "pgvector" : "vector",
                    score,
                    chunk.getOffsetStart(),
                    chunk.getOffsetEnd(),
                    extractHighlightKeyword(chunk.getContent(), question)
            ));
            noteCount.put(chunk.getNoteId(), used + 1);
            if (results.size() >= topK) {
                break;
            }
        }
        return results;
    }

    private List<ScoredChunk> searchWithPgVector(
            Long userId,
            Long workspaceId,
            Long noteId,
            float[] queryVector,
            int topK
    ) {
        int candidateLimit = Math.max(topK * 8, 32);
        String queryVectorLiteral;
        try {
            queryVectorLiteral = pgVectorSupport.toPgVector(queryVector);
        } catch (IllegalArgumentException ex) {
            return searchInMemory(userId, workspaceId, noteId, queryVector, topK);
        }
        List<NoteRagVectorSearchRow> rows = noteRagChunkMapper.searchByVectorSimilarity(
                userId,
                workspaceId,
                noteId,
                queryVectorLiteral,
                MIN_SIMILARITY,
                candidateLimit
        );
        List<ScoredChunk> scored = new ArrayList<>();
        for (NoteRagVectorSearchRow row : rows) {
            scored.add(new ScoredChunk(toEntity(row), row.getSimilarity() == null ? 0D : row.getSimilarity()));
        }
        return scored;
    }

    private List<ScoredChunk> searchInMemory(
            Long userId,
            Long workspaceId,
            Long noteId,
            float[] queryVector,
            int topK
    ) {
        var wrapper = Wrappers.<NoteRagChunkEntity>lambdaQuery()
                .eq(NoteRagChunkEntity::getUserId, userId);
        if (workspaceId != null) {
            wrapper.eq(NoteRagChunkEntity::getWorkspaceId, workspaceId);
        }
        if (noteId != null) {
            wrapper.eq(NoteRagChunkEntity::getNoteId, noteId);
        }
        int maxScan = Math.max(aiProperties.getRagMaxChunksScan(), topK);
        wrapper.orderByDesc(NoteRagChunkEntity::getUpdatedAt).last("LIMIT " + maxScan);
        List<NoteRagChunkEntity> stored = noteRagChunkMapper.selectList(wrapper);
        if (stored.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (NoteRagChunkEntity chunk : stored) {
            float[] vector = deserializeEmbedding(chunk.getEmbedding());
            double similarity = com.noto.zhihui.common.util.VectorMath.cosineSimilarity(queryVector, vector);
            if (similarity < MIN_SIMILARITY) {
                continue;
            }
            scored.add(new ScoredChunk(chunk, similarity));
        }
        return scored;
    }

    private NoteRagChunkEntity toEntity(NoteRagVectorSearchRow row) {
        NoteRagChunkEntity entity = new NoteRagChunkEntity();
        entity.setId(row.getId());
        entity.setNoteId(row.getNoteId());
        entity.setWorkspaceId(row.getWorkspaceId());
        entity.setUserId(row.getUserId());
        entity.setChunkIndex(row.getChunkIndex());
        entity.setContent(row.getContent());
        entity.setOffsetStart(row.getOffsetStart());
        entity.setOffsetEnd(row.getOffsetEnd());
        entity.setEmbedding(row.getEmbedding());
        entity.setEmbeddingModel(row.getEmbeddingModel());
        entity.setCreatedAt(row.getCreatedAt());
        entity.setUpdatedAt(row.getUpdatedAt());
        return entity;
    }

    private int keywordOverlapScore(String content, String question) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(question)) {
            return 0;
        }
        String lower = content.toLowerCase();
        int boost = 0;
        for (String token : question.trim().split("[\\s,，。！？；：、.!?;:]+")) {
            if (token.length() >= 2 && lower.contains(token.toLowerCase())) {
                boost += 4;
            }
        }
        return Math.min(boost, 20);
    }

    private String loadNoteTitle(Long noteId, Long userId) {
        try {
            NoteEntity note = noteService.requireOwnedNote(noteId, userId);
            return note.getTitle() == null ? "无标题" : note.getTitle();
        } catch (Exception ex) {
            return "文档 #" + noteId;
        }
    }

    private String extractHighlightKeyword(String content, String question) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(question)) {
            return "";
        }
        String[] tokens = question.trim().split("[\\s,，。！？；：、.!?;:]+");
        for (String token : tokens) {
            if (token.length() >= 2 && content.toLowerCase().contains(token.toLowerCase())) {
                return token;
            }
        }
        return question.length() > 16 ? question.substring(0, 16) : question;
    }

    private float[] deserializeEmbedding(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<float[]>() {});
        } catch (Exception ex) {
            return new float[0];
        }
    }

    private record ScoredChunk(NoteRagChunkEntity chunk, double similarity) {
    }
}
