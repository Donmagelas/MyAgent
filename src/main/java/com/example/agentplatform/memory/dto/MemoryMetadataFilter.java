package com.example.agentplatform.memory.dto;

import java.util.Locale;

/**
 * 记忆元数据过滤条件。
 * 当前先支持自动提炼相关的固定字段过滤，避免直接暴露过于泛化的 JSON DSL。
 */
public record MemoryMetadataFilter(
        Boolean autoExtracted,
        String triggerType,
        Long assistantMessageId
) {

    /**
     * 过滤空值并规范化 triggerType。
     */
    public MemoryMetadataFilter normalize() {
        String normalizedTriggerType = triggerType == null || triggerType.isBlank()
                ? null
                : triggerType.trim().toUpperCase(Locale.ROOT);
        return new MemoryMetadataFilter(autoExtracted, normalizedTriggerType, assistantMessageId);
    }

    /**
     * 判断是否包含至少一个有效过滤条件。
     */
    public boolean hasConstraint() {
        return autoExtracted != null
                || (triggerType != null && !triggerType.isBlank())
                || assistantMessageId != null;
    }
}
