package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        List<String> blockedKeywords,
        boolean classifierEnabled,
        double classifierTemperature,
        int classifierMaxTokens,
        boolean maybeProbeEnabled,
        int maybeProbeMinHits,
        double maybeProbeMinScore,
        double mustRagConfidenceThreshold
) {
    /**
     * 显式指定使用 record 的规范构造器做配置绑定。
     * 这样即使保留兼容旧测试的重载构造器，live 启动时也不会退回到默认构造实例化。
     */
    @ConstructorBinding
    public AgentRagRoutingProperties {
    }

    /**
     * 兼容旧测试和旧构造代码，新增 AI 分类与探测参数使用默认值。
     */
    public AgentRagRoutingProperties(
            boolean enabled,
            int minQueryLength,
            int minPositiveSignals,
            List<String> questionKeywords,
            List<String> domainKeywords,
            List<String> blockedKeywords
    ) {
        this(
                enabled,
                minQueryLength,
                minPositiveSignals,
                questionKeywords,
                domainKeywords,
                blockedKeywords,
                true,
                0.0d,
                256,
                true,
                1,
                0.18d,
                0.72d
        );
    }
}
