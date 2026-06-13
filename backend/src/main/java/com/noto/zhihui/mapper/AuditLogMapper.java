package com.noto.zhihui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noto.zhihui.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
