package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("share_link")
public class ShareLinkEntity extends BaseEntity {

    private String token;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("created_by")
    private Long createdBy;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    private Boolean enabled;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("view_count")
    private Long viewCount;

    @TableField("last_viewed_at")
    private LocalDateTime lastViewedAt;
}
