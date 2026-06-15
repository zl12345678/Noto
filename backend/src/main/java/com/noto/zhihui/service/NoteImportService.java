package com.noto.zhihui.service;

import com.noto.zhihui.dto.note.NoteClipImportRequest;
import com.noto.zhihui.vo.note.NoteImportVO;

import org.springframework.web.multipart.MultipartFile;

public interface NoteImportService {

    NoteImportVO importClip(NoteClipImportRequest request, Long userId);

    NoteImportVO importFile(MultipartFile file, Long workspaceId, Long folderId, String title, Long userId);
}
