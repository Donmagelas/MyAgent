package com.example.agentplatform.chat.service;

import reactor.core.publisher.Flux;

/**
 * 聊天补全流式客户端接口。
 * 负责把模型侧 token streaming 统一转换为后端可消费的增量结果。
 */
public interface ChatCompletionStreamClient {

    /**
     * 使用默认温度发起一次流式聊天补全。
     * systemPrompt 用于系统约束，userMessage 用于用户输入。
     */
    default Flux<ChatCompletionChunk> stream(String systemPrompt, String userMessage) {
        return stream(systemPrompt, userMessage, null);
    }

    /**
     * 使用指定温度发起一次流式聊天补全。
     * temperature 为 null 时使用全局默认温度。
     */
    Flux<ChatCompletionChunk> stream(String systemPrompt, String userMessage, Double temperature);

    /**
     * 流式增量片段，最后一个片段携带完整 usage 信息。
     */
    record ChatCompletionChunk(
            String requestId,
            String modelName,
            String delta,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            boolean completed
    ) {
    }
}
