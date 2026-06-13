package com.noto.zhihui.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.config.NotoAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import java.util.List;
import java.util.Map;

@Component
@DependsOn("databaseSchemaMigrator")
public class PgVectorSupport {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSupport.class);
    private static final int BACKFILL_BATCH = 500;

    private final JdbcTemplate jdbcTemplate;
    private final NotoAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private volatile boolean available;

    public PgVectorSupport(JdbcTemplate jdbcTemplate, NotoAiProperties aiProperties, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        if (!aiProperties.isRagPgvectorEnabled()) {
            available = false;
            log.info("RAG pgvector disabled by configuration");
            return;
        }
        available = tryInitializeSchema();
        if (available) {
            backfillMissingVectors();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public int embeddingDimensions() {
        return Math.max(aiProperties.getRagEmbeddingDimensions(), 1);
    }

    public String toPgVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("empty embedding vector");
        }
        if (vector.length != embeddingDimensions()) {
            throw new IllegalArgumentException(
                    "embedding dimension mismatch: expected " + embeddingDimensions() + ", got " + vector.length
            );
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    public void storeEmbeddingVector(Long chunkId, float[] vector) {
        if (!available || chunkId == null || vector == null || vector.length == 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE note_rag_chunk SET embedding_vector = ?::vector, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    toPgVector(vector),
                    chunkId
            );
        } catch (Exception ex) {
            log.warn("Failed to store pgvector embedding for chunk {}: {}", chunkId, ex.getMessage());
        }
    }

    private boolean tryInitializeSchema() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            int dimensions = embeddingDimensions();
            jdbcTemplate.execute(
                    "ALTER TABLE note_rag_chunk ADD COLUMN IF NOT EXISTS embedding_vector vector(" + dimensions + ")"
            );
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_note_rag_chunk_embedding_hnsw
                    ON note_rag_chunk USING hnsw (embedding_vector vector_cosine_ops)
                    WHERE deleted = FALSE AND embedding_vector IS NOT NULL
                    """);
            log.info("RAG pgvector enabled (dimensions={})", dimensions);
            return true;
        } catch (Exception ex) {
            log.warn("RAG pgvector unavailable, falling back to in-memory scan: {}", ex.getMessage());
            return false;
        }
    }

    private void backfillMissingVectors() {
        int dimensions = embeddingDimensions();
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                            SELECT id, embedding
                            FROM note_rag_chunk
                            WHERE deleted = FALSE
                              AND embedding IS NOT NULL
                              AND embedding <> ''
                              AND embedding_vector IS NULL
                            ORDER BY updated_at DESC
                            LIMIT ?
                            """,
                    BACKFILL_BATCH
            );
            if (rows.isEmpty()) {
                return;
            }
            int updated = 0;
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                String raw = row.get("embedding") == null ? null : row.get("embedding").toString();
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                try {
                    float[] vector = objectMapper.readValue(raw, new TypeReference<float[]>() {});
                    if (vector.length != dimensions) {
                        continue;
                    }
                    storeEmbeddingVector(id, vector);
                    updated++;
                } catch (Exception ignored) {
                    // 跳过无法解析的历史向量
                }
            }
            log.info("RAG pgvector backfill updated {} chunks", updated);
            if (rows.size() < BACKFILL_BATCH) {
                return;
            }
        }
    }
}
