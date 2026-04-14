package com.example.agentplatform.agent.domain;

/**
 * CoT 单轮推理结果。
 * 仅保留摘要版推理说明和最终答案，不把完整思维链直接暴露给调用方。
 */
public record AgentCotResult(
        String reasoningSummary,
        String finalAnswer
) {
}
