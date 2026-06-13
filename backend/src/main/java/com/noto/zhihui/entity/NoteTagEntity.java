package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_tag")
public class NoteTagEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;
    private Long tagId;
    private LocalDateTime createdAt;
}
