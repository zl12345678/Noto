package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("share_link_item")
public class ShareLinkItemEntity extends BaseEntity {

    @TableField("share_link_id")
    private Long shareLinkId;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("sort_order")
    private Integer sortOrder;
}
