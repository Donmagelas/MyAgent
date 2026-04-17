package com.example.agentplatform.agent.domain;

/**
 * AI 对当前问题是否需要进入 RAG 的结构化判断。
 */
public enum RagIntentDecision {

    /**
     * 明确需要先检索知识库。
     */
    MUST_RAG,

    /**
     * 可能需要检索，需要再做一次轻量检索探测。
     */
    MAYBE_RAG,

    /**
     * 当前问题不应主动进入 RAG。
     */
    NO_RAG
}
