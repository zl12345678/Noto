package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_rag_chunk")
public class NoteRagChunkEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("note_id")
    private Long noteId;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("user_id")
    private Long userId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String content;

    @TableField("offset_start")
    private Integer offsetStart;

    @TableField("offset_end")
    private Integer offsetEnd;

    private String embedding;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
