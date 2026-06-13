package com.noto.zhihui.controller.share;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.share.ShareBatchCreateRequest;
import com.noto.zhihui.dto.share.ShareCreateRequest;
import com.noto.zhihui.dto.share.ShareUnlockRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.ShareLinkService;
import com.noto.zhihui.vo.share.ShareLinkVO;
import com.noto.zhihui.vo.share.SharedContentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/share")
public class ShareController {

    private final ShareLinkService shareLinkService;

    public ShareController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<ShareLinkVO>> listMyShares() {
        return ApiResponse.success(shareLinkService.listMyShares(requireUserId()), null);
    }

    @GetMapping("/notes/{noteId}")
    public ApiResponse<ShareLinkVO> getNoteShare(@PathVariable Long noteId) {
        return ApiResponse.success(shareLinkService.getNoteShare(noteId, requireUserId()), null);
    }

    @PostMapping("/notes/{noteId}")
    public ApiResponse<ShareLinkVO> createNoteShare(
            @PathVariable Long noteId,
            @RequestBody(required = false) ShareCreateRequest request
    ) {
        return ApiResponse.success(shareLinkService.createNoteShare(noteId, request, requireUserId()), null);
    }

    @DeleteMapping("/notes/{noteId}")
    public ApiResponse<Void> revokeNoteShare(@PathVariable Long noteId) {
        shareLinkService.revokeNoteShare(noteId, requireUserId());
        return ApiResponse.success(null, null);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ApiResponse<ShareLinkVO> getAttachmentShare(@PathVariable Long attachmentId) {
        return ApiResponse.success(shareLinkService.getAttachmentShare(attachmentId, requireUserId()), null);
    }

    @PostMapping("/attachments/{attachmentId}")
    public ApiResponse<ShareLinkVO> createAttachmentShare(
            @PathVariable Long attachmentId,
            @RequestBody(required = false) ShareCreateRequest request
    ) {
        return ApiResponse.success(shareLinkService.createAttachmentShare(attachmentId, request, requireUserId()), null);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> revokeAttachmentShare(@PathVariable Long attachmentId) {
        shareLinkService.revokeAttachmentShare(attachmentId, requireUserId());
        return ApiResponse.success(null, null);
    }

    @PostMapping("/attachments/batch")
    public ApiResponse<ShareLinkVO> createBatchAttachmentShare(@Valid @RequestBody ShareBatchCreateRequest request) {
        return ApiResponse.success(shareLinkService.createBatchAttachmentShare(request, requireUserId()), null);
    }

    @GetMapping("/links/{token}")
    public ApiResponse<ShareLinkVO> getShareInfo(@PathVariable String token) {
        return ApiResponse.success(shareLinkService.getShareInfo(token, requireUserId()), null);
    }

    @DeleteMapping("/links/{token}")
    public ApiResponse<Void> revokeShareByToken(@PathVariable String token) {
        shareLinkService.revokeShareByToken(token, requireUserId());
        return ApiResponse.success(null, null);
    }

    @GetMapping("/public/{token}")
    public ApiResponse<SharedContentVO> getPublicContent(@PathVariable String token) {
        return ApiResponse.success(shareLinkService.getPublicContent(token), null);
    }

    @PostMapping("/public/{token}/unlock")
    public ApiResponse<SharedContentVO> unlockPublicContent(
            @PathVariable String token,
            @RequestBody(required = false) ShareUnlockRequest request
    ) {
        String password = request != null ? request.getPassword() : null;
        return ApiResponse.success(shareLinkService.unlockPublicContent(token, password), null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
