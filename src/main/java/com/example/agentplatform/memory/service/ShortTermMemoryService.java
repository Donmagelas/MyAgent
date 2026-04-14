package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.RecentConversationMessage;

import java.util.List;

/**
 * 短期记忆服务。
 * 对外提供按会话窗口读取最近消息的能力。
 */
public interface ShortTermMemoryService {

    /** 读取当前会话最近 N 条消息。 */
    List<RecentConversationMessage> loadRecentMessages(Long userId, Long conversationId, int limit);
}
