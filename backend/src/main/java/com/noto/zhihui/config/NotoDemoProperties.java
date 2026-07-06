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

    /** 写入演示数据的目标用户名；生产演示应使用独立 demo 账号。 */
    private String username = "demo";

    /** 目标用户不存在且 createUser=true 时使用的初始密码。 */
    private String password = "123456";

    /** 目标用户不存在且 createUser=true 时使用的邮箱。 */
    private String email = "demo@noto.local";

    /** 目标用户不存在且 createUser=true 时使用的昵称。 */
    private String nickname = "演示账号";

    /** 目标用户不存在时是否自动创建；默认关闭以保持生产环境显式配置。 */
    private boolean createUser = false;

    /** 目标用户已存在时是否把密码同步为 password；仅建议用于独立演示账号。 */
    private boolean resetPassword = false;
}
