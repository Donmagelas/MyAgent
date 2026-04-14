package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.repository.ShortTermMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认短期记忆服务实现。
 * 直接委托仓储按 conversation 窗口读取最近消息。
 */
@Service
public class DefaultShortTermMemoryService implements ShortTermMemoryService {

    private final ShortTermMemoryRepository shortTermMemoryRepository;

    public DefaultShortTermMemoryService(ShortTermMemoryRepository shortTermMemoryRepository) {
        this.shortTermMemoryRepository = shortTermMemoryRepository;
    }

    @Override
    public List<RecentConversationMessage> loadRecentMessages(Long userId, Long conversationId, int limit) {
        return shortTermMemoryRepository.findRecentMessages(userId, conversationId, limit);
    }
}
