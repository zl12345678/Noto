package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class UserEntity extends BaseEntity {

    private String username;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
}
