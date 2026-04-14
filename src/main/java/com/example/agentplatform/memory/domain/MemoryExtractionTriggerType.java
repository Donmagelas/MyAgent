package com.example.agentplatform.memory.domain;

/**
 * 自动长期记忆提炼触发类型。
 * 用于区分周期触发和重要内容触发，便于后续观测与调优。
 */
public enum MemoryExtractionTriggerType {
    PERIODIC,
    IMPORTANT_CONTENT
}
