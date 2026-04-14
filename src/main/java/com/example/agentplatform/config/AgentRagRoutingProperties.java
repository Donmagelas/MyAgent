package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 内部 RAG 进入判定配置。
 * 用于控制启发式判定是否开启，以及识别知识型问题时使用的关键词集合。
 */
@ConfigurationProperties(prefix = "app.agent.rag-routing")
public record AgentRagRoutingProperties(
        boolean enabled,
        int minQueryLength,
        int minPositiveSignals,
        List<String> questionKeywords,
        List<String> domainKeywords,
        List<String> blockedKeywords
) {
}
