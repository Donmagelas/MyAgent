package com.example.agentplatform.chat.service;

/**
 * 聊天补全客户端接口。
 * 屏蔽底层模型提供方差异，统一返回回答正文和 token usage。
 */
public interface ChatCompletionClient {

    /**
     * 使用默认温度完成一次聊天补全。
     * systemPrompt 用于系统约束，userMessage 用于用户输入。
     */
    default ChatCompletionResult complete(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, null);
    }

    /**
     * 使用指定温度完成一次聊天补全。
     * temperature 为 null 时使用全局默认温度。
     */
    ChatCompletionResult complete(String systemPrompt, String userMessage, Double temperature);

    /**
     * 模型补全结果和 usage 元数据。
     */
    record ChatCompletionResult(
            String requestId,
            String modelName,
            String answer,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }
}
