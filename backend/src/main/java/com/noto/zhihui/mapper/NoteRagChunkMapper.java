package com.noto.zhihui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noto.zhihui.entity.NoteRagChunkEntity;
import com.noto.zhihui.vo.rag.NoteRagVectorSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoteRagChunkMapper extends BaseMapper<NoteRagChunkEntity> {

    @Select("""
            <script>
            SELECT id,
                   note_id AS noteId,
                   workspace_id AS workspaceId,
                   user_id AS userId,
                   chunk_index AS chunkIndex,
                   content,
                   offset_start AS offsetStart,
                   offset_end AS offsetEnd,
                   embedding,
                   embedding_model AS embeddingModel,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   1 - (embedding_vector &lt;=&gt; #{queryVector}::vector) AS similarity
            FROM note_rag_chunk
            WHERE user_id = #{userId}
              AND deleted = FALSE
              AND embedding_vector IS NOT NULL
              <if test="workspaceId != null">
                AND workspace_id = #{workspaceId}
              </if>
              <if test="noteId != null">
                AND note_id = #{noteId}
              </if>
              AND 1 - (embedding_vector &lt;=&gt; #{queryVector}::vector) &gt;= #{minSimilarity}
            ORDER BY embedding_vector &lt;=&gt; #{queryVector}::vector
            LIMIT #{limit}
            </script>
            """)
    List<NoteRagVectorSearchRow> searchByVectorSimilarity(
            @Param("userId") Long userId,
            @Param("workspaceId") Long workspaceId,
            @Param("noteId") Long noteId,
            @Param("queryVector") String queryVector,
            @Param("minSimilarity") double minSimilarity,
            @Param("limit") int limit
    );
}
