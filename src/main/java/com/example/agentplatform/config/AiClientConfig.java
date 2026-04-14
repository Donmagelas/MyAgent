package com.example.agentplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AI 客户端共享配置。
 * 对外提供可复用的 {@link RestClient.Builder}，供模型相关 HTTP 调用使用。
 */
@Configuration
public class AiClientConfig {

    /** 创建自定义 DashScope 客户端共用的 RestClient 构建器。 */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
