package com.example.agentplatform.chat.dto;

import java.util.List;
import java.util.Map;

/**
 * 聊天流式输出的标准 SSE 事件对象。
 * 统一承载 start、delta、sources、done、error 以及步骤级元数据。
 */
public record ChatStreamEvent(
        String type,
        String mode,
        Long conversationId,
        String sessionId,
        String content,
        List<ChatAskResponse.SourceItem> sources,
        Usage usage,
        String error,
        Map<String, Object> metadata
) {

    /**
     * 创建流式开始事件。
     */
    public static ChatStreamEvent start(String mode, Long conversationId, String sessionId) {
        return start(mode, conversationId, sessionId, null);
    }

    /**
     * 创建带元数据的流式开始事件。
     */
    public static ChatStreamEvent start(
            String mode,
            Long conversationId,
            String sessionId,
            Map<String, Object> metadata
    ) {
        return new ChatStreamEvent("start", mode, conversationId, sessionId, null, null, null, null, metadata);
    }

    /**
     * 创建用于返回 grounding 来源的事件。
     */
    public static ChatStreamEvent sources(
            String mode,
            Long conversationId,
            String sessionId,
            List<ChatAskResponse.SourceItem> sources
    ) {
        return new ChatStreamEvent("sources", mode, conversationId, sessionId, null, sources, null, null, null);
    }

    /**
     * 创建一条增量文本 delta 事件。
     */
    public static ChatStreamEvent delta(String mode, Long conversationId, String sessionId, String content) {
        return new ChatStreamEvent("delta", mode, conversationId, sessionId, content, null, null, null, null);
    }

    /**
     * 创建结构化步骤事件。
     */
    public static ChatStreamEvent step(
            String type,
            String mode,
            Long conversationId,
            String sessionId,
            String content,
            Map<String, Object> metadata
    ) {
        return new ChatStreamEvent(type, mode, conversationId, sessionId, content, null, null, null, metadata);
    }

    /**
     * 创建步骤级 usage 事件。
     */
    public static ChatStreamEvent usage(
            String mode,
            Long conversationId,
            String sessionId,
            Usage usage,
            Map<String, Object> metadata
    ) {
        return new ChatStreamEvent("usage", mode, conversationId, sessionId, null, null, usage, null, metadata);
    }

    /**
     * 创建流式成功结束事件。
     */
    public static ChatStreamEvent done(
            String mode,
            Long conversationId,
            String sessionId,
            String content,
            Usage usage
    ) {
        return new ChatStreamEvent("done", mode, conversationId, sessionId, content, null, usage, null, null);
    }

    /**
     * 创建流式失败结束事件。
     */
    public static ChatStreamEvent error(String mode, Long conversationId, String sessionId, String error) {
        return new ChatStreamEvent("error", mode, conversationId, sessionId, null, null, null, error, null);
    }

    /**
     * 若提供方支持，则在流结束时返回 usage 汇总。
     */
    public record Usage(
            String requestId,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Long latencyMs
    ) {
    }
}
