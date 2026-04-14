package com.example.agentplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 * Phase 1 先支持最小注册能力，默认创建普通聊天用户。
 */
public record AuthRegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度必须在 3 到 64 之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须在 6 到 64 之间")
        String password
) {
}
