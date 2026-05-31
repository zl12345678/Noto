package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.entity.UserEntity;

public interface UserService extends IService<UserEntity> {

    UserEntity findByUsername(String username);
}
