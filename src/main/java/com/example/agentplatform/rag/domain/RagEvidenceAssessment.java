package com.example.agentplatform.rag.domain;

/**
 * 检索证据充分性评估结果。
 * 用于在真正进入 grounded-answer 前判断当前证据是否足以支撑回答。
 */
public record RagEvidenceAssessment(
        boolean sufficient,
        String reason,
        int sourceCount,
        double topScore,
        double lexicalCoverage
) {
}
