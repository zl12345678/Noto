package com.noto.zhihui.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(0, "success", data, traceId);
    }

    public static <T> ApiResponse<T> failure(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
