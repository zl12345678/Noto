package com.noto.zhihui.vo.share;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkVO {

    private String token;
    private String sharePath;
    private String resourceType;
    private Long resourceId;
    private String title;
    private Long workspaceId;
    private Boolean enabled;
    private Boolean passwordProtected;
    private Long viewCount;
    private String lastViewedAt;
    private Integer itemCount;
    private String expiresAt;
    private String createdAt;
}
