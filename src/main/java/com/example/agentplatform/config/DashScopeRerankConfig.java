package com.example.agentplatform.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DashScope rerank 模型配置。
 * 优先复用 Spring AI Alibaba 已有的 RerankModel，而不是手写 HTTP 客户端。
 */
@Configuration
public class DashScopeRerankConfig {

    /**
     * 创建项目统一使用的 rerank 模型。
     */
    @Bean
    @Primary
    public RerankModel rerankModel(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            AiModelProperties aiModelProperties,
            @Value("${spring.ai.dashscope.api-key:}") String dashScopeApiKey
    ) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                .withModel(aiModelProperties.rerankModel())
                .withReturnDocuments(true)
                .build();
        return new DashScopeRerankModel(dashScopeApi, options);
    }
}
