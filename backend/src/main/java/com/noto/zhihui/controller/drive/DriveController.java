package com.noto.zhihui.controller.drive;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.drive.DriveBatchDownloadRequest;
import com.noto.zhihui.dto.drive.DriveFolderCreateRequest;
import com.noto.zhihui.dto.drive.DriveFolderUpdateRequest;
import com.noto.zhihui.entity.NoteAttachmentEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.BatchDownloadTicketService;
import com.noto.zhihui.service.DriveFolderService;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import com.noto.zhihui.vo.drive.BatchDownloadPrepareVO;
import com.noto.zhihui.vo.drive.DriveFolderVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drive")
public class DriveController {

    private static final Logger log = LoggerFactory.getLogger(DriveController.class);

    private final AttachmentService attachmentService;
    private final DriveFolderService driveFolderService;
    private final BatchDownloadTicketService batchDownloadTicketService;

    public DriveController(
            AttachmentService attachmentService,
            DriveFolderService driveFolderService,
            BatchDownloadTicketService batchDownloadTicketService
    ) {
        this.attachmentService = attachmentService;
        this.driveFolderService = driveFolderService;
        this.batchDownloadTicketService = batchDownloadTicketService;
    }

    @GetMapping("/files")
    public ApiResponse<List<AttachmentVO>> listFiles(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) Boolean uncategorized,
            @RequestParam(required = false) Long noteId,
            @RequestParam(required = false) Boolean unlinkedOnly,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
                attachmentService.listByWorkspace(
                        workspaceId, folderId, uncategorized, noteId, unlinkedOnly, keyword, requireUserId()),
                null
        );
    }

    @PostMapping("/upload")
    public ApiResponse<AttachmentVO> upload(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(attachmentService.uploadToDrive(workspaceId, folderId, file, requireUserId()), null);
    }

    @PatchMapping("/files/{id}/folder")
    public ApiResponse<AttachmentVO> moveToFolder(
            @PathVariable Long id,
            @RequestParam(required = false) Long folderId
    ) {
        return ApiResponse.success(attachmentService.moveToFolder(id, folderId, requireUserId()), null);
    }

    @PostMapping("/files/batch-download/prepare")
    public ApiResponse<BatchDownloadPrepareVO> prepareBatchDownload(
            @RequestBody DriveBatchDownloadRequest request
    ) throws IOException {
        Long userId = requireUserId();
        List<Long> attachmentIds = parseAttachmentIds(request.getIds());
        List<Long> folderIds = parseFolderIds(request.getFolderIds());
        validateBatchDownloadRequest(request, attachmentIds, folderIds);

        List<NoteAttachmentEntity> entities = attachmentService.resolveBatchDownloadSelection(
                attachmentIds,
                folderIds,
                request.getUncategorized(),
                request.getUnlinkedOnly(),
                request.getWorkspaceId(),
                userId
        );

        Path zipFile = attachmentService.buildBatchDownloadZipFile(entities);
        String filename = "noto-drive-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".zip";
        String ticket = batchDownloadTicketService.create(zipFile, filename, userId);

        BatchDownloadPrepareVO result = new BatchDownloadPrepareVO();
        result.setTicket(ticket);
        result.setFilename(filename);
        return ApiResponse.success(result, null);
    }

    @GetMapping("/files/batch-download/{ticket}")
    public void downloadPreparedBatch(
            @PathVariable String ticket,
            HttpServletResponse response
    ) throws IOException {
        BatchDownloadTicketService.Ticket entry = batchDownloadTicketService.consume(ticket, UserContext.getUserId());
        Path zipFile = entry.path();

        try {
            long size = Files.size(zipFile);
            String encoded = URLEncoder.encode(entry.filename(), StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/zip");
            response.setContentLengthLong(size);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

            try (InputStream inputStream = Files.newInputStream(zipFile)) {
                inputStream.transferTo(response.getOutputStream());
            }
            response.flushBuffer();
        } catch (IOException ex) {
            if (isClientAbort(ex)) {
                log.debug("Client aborted batch download stream for ticket {}", ticket);
            } else {
                log.error("Batch download stream failed for ticket {}", ticket, ex);
            }
            throw ex;
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    @GetMapping("/folders")
    public ApiResponse<List<DriveFolderVO>> listFolders(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(driveFolderService.listFolders(workspaceId, requireUserId(), keyword), null);
    }

    @PostMapping("/folders")
    public ApiResponse<DriveFolderVO> createFolder(@Valid @RequestBody DriveFolderCreateRequest request) {
        return ApiResponse.success(driveFolderService.createFolder(request, requireUserId()), null);
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<DriveFolderVO> updateFolder(
            @PathVariable Long id,
            @Valid @RequestBody DriveFolderUpdateRequest request
    ) {
        return ApiResponse.success(driveFolderService.updateFolder(id, request, requireUserId()), null);
    }

    @DeleteMapping("/folders/{id}")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id) {
        driveFolderService.deleteFolder(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    private List<Long> parseAttachmentIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String rawId : rawIds) {
            if (!StringUtils.hasText(rawId)) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(rawId.trim()));
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无效的文件 ID");
            }
        }
        return parsed.stream().distinct().toList();
    }

    private List<Long> parseFolderIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String rawId : rawIds) {
            if (!StringUtils.hasText(rawId)) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(rawId.trim()));
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无效的文件夹 ID");
            }
        }
        return parsed.stream().distinct().toList();
    }

    private void validateBatchDownloadRequest(
            DriveBatchDownloadRequest request,
            List<Long> attachmentIds,
            List<Long> folderIds
    ) {
        boolean hasFiles = !attachmentIds.isEmpty();
        boolean hasFolders = !folderIds.isEmpty();
        boolean hasVirtual = Boolean.TRUE.equals(request.getUncategorized())
                || Boolean.TRUE.equals(request.getUnlinkedOnly());
        if (!hasFiles && !hasFolders && !hasVirtual) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "请选择要下载的文件或文件夹");
        }
        if ((hasFolders || hasVirtual) && request.getWorkspaceId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "批量下载文件夹需要指定知识库");
        }
    }

    private boolean isClientAbort(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException")
                    || className.contains("AsyncRequestNotUsableException")) {
                return true;
            }
            if (current instanceof IOException ioEx) {
                String message = ioEx.getMessage();
                if (message != null && (message.contains("Connection reset")
                        || message.contains("中止")
                        || message.contains("aborted"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
