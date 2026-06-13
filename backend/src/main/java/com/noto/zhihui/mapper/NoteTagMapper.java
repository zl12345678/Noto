package com.noto.zhihui.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noto.zhihui.entity.NoteTagEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTagEntity> {

    @Delete("DELETE FROM note_tag WHERE note_id = #{noteId}")
    void physicalDeleteByNoteId(@Param("noteId") Long noteId);
}
