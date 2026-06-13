package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.mapper.TagMapper;
import com.noto.zhihui.service.TagService;
import org.springframework.stereotype.Service;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, TagEntity> implements TagService {
}
