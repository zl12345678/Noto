package com.noto.zhihui.common.exception;

import com.noto.zhihui.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Object> handleBizException(BizException ex, HttpServletRequest request) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage(), getTraceId(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Object> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ErrorCode.BAD_REQUEST.getMessage();
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            message = fieldError.getDefaultMessage();
        }
        return ApiResponse.failure(ErrorCode.BAD_REQUEST.getCode(), message, getTraceId(request));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception ex, HttpServletRequest request) {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), getTraceId(request));
    }

    private String getTraceId(HttpServletRequest request) {
        return request.getHeader("X-Trace-Id");
    }
}
