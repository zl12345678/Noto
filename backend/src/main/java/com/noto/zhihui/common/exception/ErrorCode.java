package com.noto.zhihui.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(40001, "参数错误"),
    UNAUTHORIZED(40002, "未登录"),
    FORBIDDEN(40003, "无权限"),
    NOT_FOUND(40004, "资源不存在"),
    TOO_MANY_REQUESTS(40005, "请求频率过高"),
    INTERNAL_ERROR(50001, "服务器内部错误"),
    AI_ERROR(50002, "AI 服务调用失败"),
    EXTERNAL_ERROR(50003, "外部依赖失败");

    private final int code;
    private final String message;
}
