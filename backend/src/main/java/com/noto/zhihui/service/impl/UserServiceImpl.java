package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.entity.UserEntity;
import com.noto.zhihui.mapper.UserMapper;
import com.noto.zhihui.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public UserEntity findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return query().eq("username", username.trim()).one();
    }

    @Override
    public UserEntity findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return query().eq("email", email.trim()).one();
    }
}
