package com.novelplatform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(e.status).body(new ApiError(e.code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage()).findFirst().orElse("请检查输入内容");
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "请求字段或格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        // 不记录请求正文或异常消息，避免凭据和数据库连接信息进入日志。
        log.error("Unexpected server exception: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("SERVER_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
