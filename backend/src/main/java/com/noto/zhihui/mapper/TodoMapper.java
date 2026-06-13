package com.noto.zhihui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noto.zhihui.entity.TodoItemEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoMapper extends BaseMapper<TodoItemEntity> {
}
