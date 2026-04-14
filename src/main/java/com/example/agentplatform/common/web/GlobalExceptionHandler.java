package com.example.agentplatform.common.web;

import com.example.agentplatform.common.exception.ApplicationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * 全局异常处理器。
 * 把校验异常、业务异常和未预期异常统一转换成稳定的接口响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常。 */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(ApplicationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("APPLICATION_ERROR", exception.getMessage(), OffsetDateTime.now()));
    }

    /** 处理请求参数校验异常。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationException(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage(), OffsetDateTime.now()));
    }

    /** 处理 advisor 或服务层抛出的权限拒绝异常。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("ACCESS_DENIED", exception.getMessage(), OffsetDateTime.now()));
    }

    /** 兜底处理未捕获异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", exception.getMessage(), OffsetDateTime.now()));
    }
}
