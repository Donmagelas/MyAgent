package com.example.agentplatform.memory.domain;

/**
 * 带召回分数的记忆摘要视图。
 * 用于语义检索后的摘要排序与上下文组装。
 */
public record RetrievedMemorySummary(
        MemorySummary summary,
        double score
) {
}
