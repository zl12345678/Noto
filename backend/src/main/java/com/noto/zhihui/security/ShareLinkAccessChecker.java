package com.noto.zhihui.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.noto.zhihui.common.constants.ShareResourceType;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.entity.ShareLinkEntity;
import com.noto.zhihui.entity.ShareLinkItemEntity;
import com.noto.zhihui.mapper.ShareLinkItemMapper;
import com.noto.zhihui.mapper.ShareLinkMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ShareLinkAccessChecker {

    private final ShareLinkMapper shareLinkMapper;
    private final ShareLinkItemMapper shareLinkItemMapper;

    public ShareLinkAccessChecker(ShareLinkMapper shareLinkMapper, ShareLinkItemMapper shareLinkItemMapper) {
        this.shareLinkMapper = shareLinkMapper;
        this.shareLinkItemMapper = shareLinkItemMapper;
    }

    public void requireShareFileAccess(String shareToken, Long attachmentId) {
        ShareLinkEntity link = shareLinkMapper.selectOne(new LambdaQueryWrapper<ShareLinkEntity>()
                .eq(ShareLinkEntity::getToken, shareToken)
                .last("LIMIT 1"));
        if (link == null || !Boolean.TRUE.equals(link.getEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SHARE_EXPIRED);
        }
        if (ShareResourceType.ATTACHMENT.equals(link.getResourceType())) {
            if (!attachmentId.equals(link.getResourceId())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        if (ShareResourceType.BATCH.equals(link.getResourceType())) {
            boolean included = shareLinkItemMapper.exists(new LambdaQueryWrapper<ShareLinkItemEntity>()
                    .eq(ShareLinkItemEntity::getShareLinkId, link.getId())
                    .eq(ShareLinkItemEntity::getResourceId, attachmentId));
            if (!included) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }
}
