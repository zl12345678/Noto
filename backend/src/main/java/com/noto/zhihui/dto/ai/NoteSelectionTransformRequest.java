package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteSelectionTransformRequest extends NoteAiDraftRequest {

    @NotBlank
    private String mode;

    @NotBlank
    private String selectedText;
}
