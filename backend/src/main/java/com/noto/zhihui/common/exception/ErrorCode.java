package com.noto.zhihui.common.exception;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(40001, "参数错误"),
    USERNAME_EXISTS(40006, "用户名已存在"),
    EMAIL_EXISTS(40007, "邮箱已存在"),
    TAG_EXISTS(40008, "标签已存在"),
    TAG_NOT_FOUND(40009, "标签不存在"),
    UNAUTHORIZED(40002, "未登录"),
    FORBIDDEN(40003, "无权限"),
    NOT_FOUND(40004, "资源不存在"),
    TOO_MANY_REQUESTS(40005, "请求频率过高"),
    INTERNAL_ERROR(50001, "服务器内部错误"),
    AI_ERROR(50002, "AI 服务调用失败"),
    EXTERNAL_ERROR(50003, "外部依赖失败"),
    WORKSPACE_LAST_ONE(40010, "至少保留一个知识库"),
    TODO_NOT_FOUND(40011, "待办不存在"),
    REMINDER_NOT_FOUND(40012, "提醒不存在"),
    ATTACHMENT_NOT_FOUND(40013, "附件不存在"),
    STORAGE_UNAVAILABLE(40014, "文件存储未启用，请联系管理员配置 MinIO"),
    INVALID_FILE_TYPE(40015, "不支持的文件类型"),
    FILE_TOO_LARGE(40016, "文件大小超出限制"),
    SHARE_NOT_FOUND(40017, "分享链接不存在或已关闭"),
    SHARE_EXPIRED(40018, "分享链接已过期"),
    SHARE_PASSWORD_INVALID(40019, "访问密码错误");

    private final int code;
    private final String message;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
