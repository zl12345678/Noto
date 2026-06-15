package com.noto.zhihui.service.impl;

import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.common.util.HtmlClipNormalizer;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.dto.ai.NoteTransformRequest;
import com.noto.zhihui.dto.note.NoteClipImportRequest;
import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.dto.note.NoteUpdateRequest;
import com.noto.zhihui.service.AiService;
import com.noto.zhihui.service.NoteImportService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.ai.NoteTransformVO;
import com.noto.zhihui.vo.note.NoteImportVO;
import com.noto.zhihui.vo.note.NoteVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class NoteImportServiceImpl implements NoteImportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NoteService noteService;
    private final WorkspaceService workspaceService;
    private final AiService aiService;
    private final NotoAiProperties aiProperties;

    public NoteImportServiceImpl(
            NoteService noteService,
            WorkspaceService workspaceService,
            AiService aiService,
            NotoAiProperties aiProperties
    ) {
        this.noteService = noteService;
        this.workspaceService = workspaceService;
        this.aiService = aiService;
        this.aiProperties = aiProperties;
    }

    @Override
    @Transactional
    public NoteImportVO importClip(NoteClipImportRequest request, Long userId) {
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);

        String title = request.getTitle().trim();
        String normalized = HtmlClipNormalizer.normalize(request.getContent(), request.getContentType());
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "导入内容为空");
        }

        String body = buildImportBody(normalized, request.getSourceUrl());
        NoteCreateRequest createRequest = new NoteCreateRequest();
        createRequest.setWorkspaceId(request.getWorkspaceId());
        createRequest.setFolderId(request.getFolderId());
        createRequest.setTitle(title);
        createRequest.setContent(body);
        NoteVO created = noteService.createNote(createRequest, userId);

        boolean structured = false;
        if (Boolean.TRUE.equals(request.getStructureWithAi())
                && aiProperties.isEnabled()
                && StringUtils.hasText(aiProperties.getApiKey())) {
            structured = tryStructureWithAi(created, userId);
        }
        return new NoteImportVO(created.getId(), created.getTitle(), structured);
    }

    @Override
    @Transactional
    public NoteImportVO importFile(
            MultipartFile file,
            Long workspaceId,
            Long folderId,
            String title,
            Long userId
    ) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择文件");
        }
        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        if (!isSupportedImportExtension(extension)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "仅支持 .md / .markdown / .txt 文件");
        }

        String rawContent;
        try {
            rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无法读取文件内容");
        }
        if (!StringUtils.hasText(rawContent)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "文件内容为空");
        }

        NoteClipImportRequest request = new NoteClipImportRequest();
        request.setWorkspaceId(workspaceId);
        request.setFolderId(folderId);
        request.setTitle(resolveImportTitle(title, originalName));
        request.setContent(rawContent.trim());
        request.setContentType(isMarkdownExtension(extension) ? "markdown" : "plain");
        request.setStructureWithAi(false);
        return importClip(request, userId);
    }

    private boolean isSupportedImportExtension(String extension) {
        return "md".equals(extension) || "markdown".equals(extension) || "txt".equals(extension);
    }

    private boolean isMarkdownExtension(String extension) {
        return "md".equals(extension) || "markdown".equals(extension);
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String normalized = filename.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot >= normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveImportTitle(String title, String filename) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (!StringUtils.hasText(filename)) {
            return "导入文档";
        }
        String normalized = filename.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        return StringUtils.hasText(normalized) ? normalized : "导入文档";
    }

    private boolean tryStructureWithAi(NoteVO note, Long userId) {
        try {
            NoteTransformRequest transformRequest = new NoteTransformRequest();
            transformRequest.setMode("structure");
            transformRequest.setTitle(note.getTitle());
            transformRequest.setContent(note.getContent());
            NoteTransformVO transformed = aiService.transformNote(note.getId(), userId, transformRequest);

            NoteUpdateRequest update = new NoteUpdateRequest();
            update.setTitle(note.getTitle());
            update.setContent(transformed.getContent());
            noteService.updateNote(note.getId(), update, userId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildImportBody(String normalized, String sourceUrl) {
        StringBuilder builder = new StringBuilder();
        builder.append("> 导入于 ").append(LocalDateTime.now().format(STAMP));
        if (StringUtils.hasText(sourceUrl)) {
            builder.append(" · 来源 ").append(sourceUrl.trim());
        }
        builder.append("\n\n").append(normalized.trim());
        return builder.toString();
    }
}
