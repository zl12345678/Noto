package com.noto.zhihui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noto.zhihui.entity.NoteEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteMapper extends BaseMapper<NoteEntity> {
}
