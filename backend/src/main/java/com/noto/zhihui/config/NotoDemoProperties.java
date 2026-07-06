package com.noto.zhihui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "noto.demo")
public class NotoDemoProperties {

    /** 启动时写入演示笔记、待办与提醒。 */
    private boolean enabled = false;

    /** 写入演示数据的目标用户名；默认保持历史行为，写入 admin。 */
    private String username = "admin";

    /** 目标用户不存在且 createUser=true 时使用的初始密码。 */
    private String password = "admin123";

    /** 目标用户不存在且 createUser=true 时使用的邮箱。 */
    private String email = "admin@noto.com";

    /** 目标用户不存在且 createUser=true 时使用的昵称。 */
    private String nickname = "演示账号";

    /** 目标用户不存在时是否自动创建；默认关闭以保持生产环境显式配置。 */
    private boolean createUser = false;
}
