package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模型配置属性。
 * 集中声明主对话模型、向量模型、重排模型和默认温度。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiModelProperties(
        String chatModel,
        Double chatTemperature,
        String embeddingModel,
        String rerankModel
) {
}
