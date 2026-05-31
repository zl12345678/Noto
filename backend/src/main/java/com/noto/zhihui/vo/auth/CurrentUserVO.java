package com.noto.zhihui.vo.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
}
