package com.example.agentplatform.advisor.domain;

import java.util.Locale;

/**
 * 请求安全判别动作。
 * 只区分放行与拦截，避免在 advisor 层引入额外的执行状态复杂度。
 */
public enum RequestSafetyAction {
    ALLOW,
    BLOCK;

    /**
     * 将模型输出的文本动作归一化为内部枚举。
     * 未知动作按 BLOCK 处理，避免安全判别失真时误放行。
     */
    public static RequestSafetyAction from(String action) {
        if (action == null || action.isBlank()) {
            return BLOCK;
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "ALLOW" -> ALLOW;
            case "BLOCK" -> BLOCK;
            default -> BLOCK;
        };
    }
}
