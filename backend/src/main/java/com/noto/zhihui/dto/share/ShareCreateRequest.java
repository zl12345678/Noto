package com.noto.zhihui.dto.share;

import lombok.Data;

@Data
public class ShareCreateRequest {

    /** 有效天数，null 表示永久有效 */
    private Integer expiresInDays;

    /** 访问密码；null 表示更新时保留原密码，空字符串表示清除密码 */
    private String password;
}
