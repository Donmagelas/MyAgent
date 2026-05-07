package com.example.agentplatform.advisor.domain;

/**
 * 归一化后的请求安全判别结果。
 * 用于 advisor 最终决定是否阻断请求。
 */
public record RequestSafetyDecision(
        RequestSafetyAction action,
        String category,
        double confidence,
        String reason
) {

    /**
     * 根据配置阈值判断是否应当阻断。
     */
    public boolean shouldBlock(double threshold) {
        return action == RequestSafetyAction.BLOCK && confidence >= threshold;
    }
}
