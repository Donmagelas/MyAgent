package com.example.agentplatform.common.web;

import java.time.OffsetDateTime;

/**
 * 标准接口错误响应体。
 * 在控制器发生异常时由全局异常处理器返回。
 */
public record ApiErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp
) {
}
