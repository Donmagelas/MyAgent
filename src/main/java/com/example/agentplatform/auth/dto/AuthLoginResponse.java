package com.example.agentplatform.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 登录响应。
 * 返回 Bearer Token 以及当前用户的角色信息。
 */
public record AuthLoginResponse(
        Long userId,
        String username,
        String tokenType,
        String accessToken,
        OffsetDateTime expiresAt,
        List<String> roles
) {
}
