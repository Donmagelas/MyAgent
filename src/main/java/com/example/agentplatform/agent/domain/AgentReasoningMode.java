package com.example.agentplatform.agent.domain;

/**
 * Agent 推理模式。
 * COT 用于单轮链式思考，REACT 和 LOOP 用于多步迭代执行。
 */
public enum AgentReasoningMode {
    COT,
    REACT,
    LOOP
}
