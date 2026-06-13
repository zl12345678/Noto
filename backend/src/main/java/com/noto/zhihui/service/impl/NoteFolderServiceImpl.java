package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.entity.NoteFolderEntity;
import com.noto.zhihui.mapper.NoteFolderMapper;
import com.noto.zhihui.service.NoteFolderService;
import org.springframework.stereotype.Service;

@Service
public class NoteFolderServiceImpl extends ServiceImpl<NoteFolderMapper, NoteFolderEntity> implements NoteFolderService {
}
