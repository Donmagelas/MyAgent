package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 幻觉缓解配置。
 * 用于控制证据充分性判定、回答后校验以及证据不足时的降级文案。
 */
@ConfigurationProperties(prefix = "app.rag.hallucination")
public record RagHallucinationProperties(
        boolean enabled,
        String insufficientAnswer,
        Evidence evidence,
        Judge judge
) {
    /**
     * 检索证据 gate 配置。
     */
    public record Evidence(
            boolean enabled,
            int minSourceCount,
            double minTopScore,
            double minLexicalCoverage,
            int inspectedChunks
    ) {
    }

    /**
     * 回答后 judge/check 配置。
     */
    public record Judge(
            boolean enabled,
            double temperature,
            int maxTokens,
            int maxEvidenceChunks,
            int maxEvidenceChars
    ) {
    }
}
