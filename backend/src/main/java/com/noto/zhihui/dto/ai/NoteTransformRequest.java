package com.noto.zhihui.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteTransformRequest extends NoteAiDraftRequest {

    @NotBlank
    private String mode;
}
