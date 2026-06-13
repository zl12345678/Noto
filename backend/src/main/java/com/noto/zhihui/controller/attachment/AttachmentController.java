package com.noto.zhihui.controller.attachment;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.vo.attachment.AttachmentVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/attachments/upload")
    public ApiResponse<AttachmentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("noteId") Long noteId
    ) {
        return ApiResponse.success(attachmentService.upload(noteId, file, requireUserId()), null);
    }

    @GetMapping("/notes/{noteId}/attachments")
    public ApiResponse<List<AttachmentVO>> listByNote(@PathVariable Long noteId) {
        return ApiResponse.success(attachmentService.listByNote(noteId, requireUserId()), null);
    }

    @PostMapping("/attachments/{id}/link")
    public ApiResponse<AttachmentVO> linkToNote(
            @PathVariable Long id,
            @RequestParam Long noteId
    ) {
        return ApiResponse.success(attachmentService.linkToNote(id, noteId, requireUserId()), null);
    }

    @DeleteMapping("/attachments/{id}/link")
    public ApiResponse<Void> unlinkFromNote(
            @PathVariable Long id,
            @RequestParam Long noteId
    ) {
        attachmentService.unlinkFromNote(id, noteId, requireUserId());
        return ApiResponse.success(null, null);
    }

    @DeleteMapping("/attachments/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    @GetMapping("/attachments/{id}/file")
    public void downloadFile(
            @PathVariable Long id,
            @RequestParam("access") String access,
            @RequestParam(value = "share", required = false) String share,
            HttpServletResponse response
    ) {
        AttachmentVO attachment = attachmentService.requireAttachment(id);
        try (InputStream inputStream = attachmentService.openFileStream(id, access, share)) {
            String contentType = StringUtils.hasText(attachment.getFileType())
                    ? attachment.getFileType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            response.setContentType(contentType);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
            if (StringUtils.hasText(attachment.getFileName())) {
                String encoded = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                        .replace("+", "%20");
                String disposition = shouldForceDownload(contentType) ? "attachment" : "inline";
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encoded);
            }
            try (OutputStream outputStream = response.getOutputStream()) {
                inputStream.transferTo(outputStream);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.EXTERNAL_ERROR.getCode(), "文件读取失败");
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private boolean shouldForceDownload(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String mime = contentType.toLowerCase().split(";")[0].trim();
        return mime.startsWith("text/") || mime.contains("html") || mime.contains("xml") || mime.contains("javascript");
    }
}
