package com.example.agentplatform.agent.domain;

/**
 * Agent 单步动作类型。
 * TOOL 表示继续调用工具，FINAL 表示当前步骤直接产出最终答案。
 */
public enum AgentActionType {
    RAG,
    TOOL,
    FINAL
}
