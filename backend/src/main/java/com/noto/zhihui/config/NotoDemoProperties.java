package com.noto.zhihui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "noto.demo")
public class NotoDemoProperties {

    /** 启动时写入演示笔记、待办与提醒（仅 admin 账号，且仅执行一次） */
    private boolean enabled = false;
}
