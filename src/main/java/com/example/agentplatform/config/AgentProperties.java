package com.example.agentplatform.config;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 配置属性。
 * 涵盖 CoT、规划、Loop 与 subagent 等运行时参数。
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        boolean enabled,
        AgentReasoningMode defaultMode,
        Cot cot,
        Planning planning,
        Loop loop,
        Subagent subagent
) {

    /**
     * CoT 单轮推理配置。
     */
    public record Cot(
            double temperature,
            int maxTokens
    ) {
    }

    /**
     * 任务规划配置。
     */
    public record Planning(
            boolean enabled,
            double temperature,
            int maxTokens
    ) {
    }

    /**
     * ReAct / Agent Loop 配置。
     */
    public record Loop(
            int maxIterations,
            double plannerTemperature,
            int plannerMaxTokens
    ) {
    }

    /**
     * subagent 运行配置。
     */
    public record Subagent(
            boolean enabled,
            int maxTurns,
            boolean planningEnabled,
            int maxConsecutiveNoProgress,
            int maxRepeatedAction,
            List<String> allowedTools
    ) {
    }
}
