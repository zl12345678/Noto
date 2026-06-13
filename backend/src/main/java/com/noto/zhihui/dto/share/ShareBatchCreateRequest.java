package com.noto.zhihui.dto.share;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ShareBatchCreateRequest {

    @NotEmpty(message = "请选择要分享的文件")
    @Size(max = 50, message = "单次最多分享 50 个文件")
    private List<Long> ids;

    /** 有效天数，null 表示永久有效 */
    private Integer expiresInDays;

    /** 访问密码，空字符串表示不设密码 */
    private String password;
}
