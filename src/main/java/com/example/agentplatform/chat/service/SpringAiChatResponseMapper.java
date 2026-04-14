package com.example.agentplatform.chat.service;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Spring AI 聊天响应映射器。
 * 负责把 ChatResponse 适配为项目内部统一的聊天结果结构。
 */
@Component
public class SpringAiChatResponseMapper {

    /**
     * 从 Spring AI ChatResponse 中提取最终文本答案。
     */
    public String extractAnswer(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String answer = chatResponse.getResult().getOutput().getText();
        return answer == null ? "" : answer;
    }

    /**
     * 把 Spring AI ChatResponse 映射为项目统一的聊天结果对象。
     */
    public ChatCompletionClient.ChatCompletionResult toResult(ChatResponse chatResponse) {
        String answer = extractAnswer(chatResponse);
        ChatResponseMetadata metadata = chatResponse == null ? null : chatResponse.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        return new ChatCompletionClient.ChatCompletionResult(
                metadata == null ? null : metadata.getId(),
                metadata == null ? "unknown" : metadata.getModel(),
                answer,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens()
        );
    }

    /**
     * 根据流式聚合结果构建项目统一的聊天结果对象。
     */
    public ChatCompletionClient.ChatCompletionResult toResult(
            String requestId,
            String modelName,
            String answer,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        return new ChatCompletionClient.ChatCompletionResult(
                requestId,
                modelName == null || modelName.isBlank() ? "unknown" : modelName,
                answer == null ? "" : answer,
                promptTokens,
                completionTokens,
                totalTokens
        );
    }
}
