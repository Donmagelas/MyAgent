package com.example.agentplatform.observability.domain;

/**
 * 步骤级模型 usage 记录对象。
 * 保留足够字段，便于后续聚合、链路追踪和成本分析。
 */
public record ModelUsageRecord(
        Long workflowId,
        Long taskId,
        Long conversationId,
        Long messageId,
        String requestId,
        String stepName,
        String modelName,
        String provider,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        boolean success,
        String errorMessage
) {

    /**
     * 兼容旧调用方式的便捷构造器。
     */
    public ModelUsageRecord(
            Long conversationId,
            Long messageId,
            String requestId,
            String stepName,
            String modelName,
            String provider,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Long latencyMs,
            boolean success,
            String errorMessage
    ) {
        this(
                null,
                null,
                conversationId,
                messageId,
                requestId,
                stepName,
                modelName,
                provider,
                promptTokens,
                completionTokens,
                totalTokens,
                latencyMs,
                success,
                errorMessage
        );
    }
}
