package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Advisor 相关配置。
 * 当前主要用于请求安全判别，而不是硬编码词表拦截。
 */
@ConfigurationProperties(prefix = "app.advisor")
public record AdvisorProperties(
        RequestSafety requestSafety
) {

    public AdvisorProperties {
        if (requestSafety == null) {
            requestSafety = new RequestSafety(
                    true,
                    0.0d,
                    256,
                    0.60d,
                    "我不能帮助实施危险、违法、侵害隐私、越权取数、绕过安全控制或其他可能伤害个人与社会的行为。如果你的目的是防护、合规、审计或风险分析，我可以改为提供安全建议。"
            );
        }
    }

    /**
     * 请求安全判别配置。
     */
    public record RequestSafety(
            boolean enabled,
            double temperature,
            int maxTokens,
            double blockConfidenceThreshold,
            String refusalMessage
    ) {

        public RequestSafety {
            temperature = Double.isNaN(temperature) || temperature < 0.0d ? 0.0d : temperature;
            maxTokens = maxTokens <= 0 ? 256 : maxTokens;
            if (Double.isNaN(blockConfidenceThreshold)) {
                blockConfidenceThreshold = 0.60d;
            }
            blockConfidenceThreshold = Math.max(0.0d, Math.min(1.0d, blockConfidenceThreshold));
            if (refusalMessage == null || refusalMessage.isBlank()) {
                refusalMessage = "我不能帮助实施危险、违法、侵害隐私、越权取数、绕过安全控制或其他可能伤害个人与社会的行为。如果你的目的是防护、合规、审计或风险分析，我可以改为提供安全建议。";
            }
        }
    }
}
