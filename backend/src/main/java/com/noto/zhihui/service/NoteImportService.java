package com.noto.zhihui.service;

import com.noto.zhihui.dto.note.NoteClipImportRequest;
import com.noto.zhihui.vo.note.NoteImportVO;

public interface NoteImportService {

    NoteImportVO importClip(NoteClipImportRequest request, Long userId);
}
