package com.noto.zhihui.common.exception;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.config.NotoMinioProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final NotoMinioProperties minioProperties;

    public GlobalExceptionHandler(NotoMinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @ExceptionHandler(BizException.class)
    public ApiResponse<Object> handleBizException(
            BizException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (shouldSkipResponseBody(response, ex)) {
            return null;
        }
        return ApiResponse.failure(ex.getCode(), ex.getMessage(), getTraceId(request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Object> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (shouldSkipResponseBody(response, ex)) {
            return null;
        }
        String message = ErrorCode.BAD_REQUEST.getMessage();
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            message = fieldError.getDefaultMessage();
        }
        return ApiResponse.failure(ErrorCode.BAD_REQUEST.getCode(), message, getTraceId(request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Object> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (shouldSkipResponseBody(response, ex)) {
            return null;
        }
        log.warn("Upload size exceeded on {} {}", request.getMethod(), request.getRequestURI());
        return ApiResponse.failure(
                ErrorCode.FILE_TOO_LARGE.getCode(),
                minioProperties.fileTooLargeMessage(),
                getTraceId(request)
        );
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (shouldSkipResponseBody(response, ex)) {
            return null;
        }
        log.error("Unhandled {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString(), ex);
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), getTraceId(request));
    }

    private boolean shouldSkipResponseBody(HttpServletResponse response, Throwable ex) {
        if (isClientAbort(ex)) {
            log.debug("Client aborted request: {}", ex.toString());
            return true;
        }
        if (response != null && response.isCommitted()) {
            log.warn("Response already committed, skip error body: {}", ex.toString());
            return true;
        }
        return false;
    }

    private boolean isClientAbort(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException")) {
                return true;
            }
            if (current instanceof IOException ioEx) {
                String message = ioEx.getMessage();
                if (message != null && (message.contains("Connection reset")
                        || message.contains("中止")
                        || message.contains("aborted"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getTraceId(HttpServletRequest request) {
        return request.getHeader("X-Trace-Id");
    }
}
