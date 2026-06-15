package com.noto.zhihui.dto.drive;

import lombok.Data;

import java.util.List;

@Data
public class DriveBatchDownloadRequest {

    /** 直接选中的附件 ID */
    private List<String> ids;

    /** 选中的文件夹 ID（含子文件夹内文件） */
    private List<String> folderIds;

    /** 虚拟目录：未分类 */
    private Boolean uncategorized;

    /** 虚拟目录：未关联文档 */
    private Boolean unlinkedOnly;

    /** 解析 folderIds / 虚拟目录时必填 */
    private Long workspaceId;
}
