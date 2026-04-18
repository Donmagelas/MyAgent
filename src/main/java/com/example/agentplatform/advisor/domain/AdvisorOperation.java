package com.example.agentplatform.advisor.domain;

/**
 * Advisor 操作类型。
 * 用于区分当前校验发生在普通对话入口还是知识检索入口。
 */
public enum AdvisorOperation {
    AGENT_STREAM,
    KNOWLEDGE_RETRIEVE
}
