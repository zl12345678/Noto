package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteRagIndexStatusVO {

    private long indexedNotes;
    private long indexedChunks;
    private String embeddingModel;
    private boolean ragAvailable;
}
