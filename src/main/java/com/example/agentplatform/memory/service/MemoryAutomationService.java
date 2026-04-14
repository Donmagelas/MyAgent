package com.example.agentplatform.memory.service;

import com.example.agentplatform.chat.domain.ChatMessage;

/**
 * 自动记忆整理服务。
 * 在对话完成后，根据触发策略异步提炼长期记忆并同步写入记忆摘要。
 */
public interface MemoryAutomationService {

    /**
     * 在一条助手消息持久化完成后触发自动长期记忆提炼。
     */
    void triggerAfterAssistantMessage(Long userId, Long conversationId, ChatMessage assistantMessage);
}
