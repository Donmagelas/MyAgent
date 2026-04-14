package com.example.agentplatform.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 注册响应。
 * 返回新建用户的基础信息，便于后续再执行登录。
 */
public record AuthRegisterResponse(
        Long userId,
        String username,
        List<String> roles,
        OffsetDateTime createdAt
) {
}
