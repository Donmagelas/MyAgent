package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 配置属性。
 * 覆盖任务规划、统一 Loop 和 subagent 等运行时参数。
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        boolean enabled,
        Planning planning,
        Loop loop,
        Subagent subagent
) {

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
     * 统一 Agent Loop 配置。
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
