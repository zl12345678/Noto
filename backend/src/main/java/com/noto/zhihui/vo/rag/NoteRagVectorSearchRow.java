package com.noto.zhihui.vo.rag;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteRagVectorSearchRow {

    private Long id;
    private Long noteId;
    private Long workspaceId;
    private Long userId;
    private Integer chunkIndex;
    private String content;
    private Integer offsetStart;
    private Integer offsetEnd;
    private String embedding;
    private String embeddingModel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double similarity;
}
