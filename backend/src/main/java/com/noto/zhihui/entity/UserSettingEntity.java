package com.noto.zhihui.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_setting")
public class UserSettingEntity extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    @TableField("setting_key")
    private String settingKey;

    @TableField("setting_value")
    private String settingValue;
}
