package com.example.agentplatform.agent.domain;

/**
 * RAG 意图分类器的结构化输出。
 */
public record RagIntentClassification(
        RagIntentDecision decision,
        double confidence,
        String reason,
        String retrievalQuery,
        boolean needsWebSearch
) {
}
