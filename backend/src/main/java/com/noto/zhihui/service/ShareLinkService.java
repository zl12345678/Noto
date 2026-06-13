package com.noto.zhihui.service;

import com.noto.zhihui.dto.share.ShareBatchCreateRequest;
import com.noto.zhihui.dto.share.ShareCreateRequest;
import com.noto.zhihui.vo.share.ShareLinkVO;
import com.noto.zhihui.vo.share.SharedContentVO;

import java.util.List;

public interface ShareLinkService {

    ShareLinkVO getNoteShare(Long noteId, Long userId);

    ShareLinkVO createNoteShare(Long noteId, ShareCreateRequest request, Long userId);

    void revokeNoteShare(Long noteId, Long userId);

    ShareLinkVO getAttachmentShare(Long attachmentId, Long userId);

    ShareLinkVO createAttachmentShare(Long attachmentId, ShareCreateRequest request, Long userId);

    void revokeAttachmentShare(Long attachmentId, Long userId);

    ShareLinkVO createBatchAttachmentShare(ShareBatchCreateRequest request, Long userId);

    ShareLinkVO getShareInfo(String token, Long userId);

    void revokeShareByToken(String token, Long userId);

    SharedContentVO getPublicContent(String token);

    SharedContentVO unlockPublicContent(String token, String password);

    List<ShareLinkVO> listMyShares(Long userId);
}
