package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.RecentConversationMessage;

import java.util.List;

/**
 * 短期记忆服务。
 * 对外提供按会话窗口读取最近消息的能力。
 */
public interface ShortTermMemoryService {

    /**
     * 读取当前会话最近 N 条消息。
     */
    List<RecentConversationMessage> loadRecentMessages(Long userId, Long conversationId, int limit);

    /**
     * 当前会话有新消息写入后，通知短期记忆窗口缓存失效。
     * 默认实现留空，便于非缓存实现直接复用接口。
     */
    default void markConversationUpdated(Long conversationId) {
        // 默认无需处理。
    }
}
