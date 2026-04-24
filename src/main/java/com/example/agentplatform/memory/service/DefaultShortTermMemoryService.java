package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.repository.ShortTermMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认短期记忆服务实现。
 * 优先读取 Redis 缓存，未命中时再回源数据库。
 */
@Service
public class DefaultShortTermMemoryService implements ShortTermMemoryService {

    private final ShortTermMemoryRepository shortTermMemoryRepository;
    private final ShortTermMemoryRedisCacheService shortTermMemoryRedisCacheService;

    public DefaultShortTermMemoryService(
            ShortTermMemoryRepository shortTermMemoryRepository,
            ShortTermMemoryRedisCacheService shortTermMemoryRedisCacheService
    ) {
        this.shortTermMemoryRepository = shortTermMemoryRepository;
        this.shortTermMemoryRedisCacheService = shortTermMemoryRedisCacheService;
    }

    @Override
    public List<RecentConversationMessage> loadRecentMessages(Long userId, Long conversationId, int limit) {
        List<RecentConversationMessage> cachedMessages =
                shortTermMemoryRedisCacheService.get(userId, conversationId, limit);
        if (cachedMessages != null) {
            return cachedMessages;
        }

        List<RecentConversationMessage> recentMessages =
                shortTermMemoryRepository.findRecentMessages(userId, conversationId, limit);
        shortTermMemoryRedisCacheService.put(userId, conversationId, limit, recentMessages);
        return recentMessages;
    }

    @Override
    public void markConversationUpdated(Long conversationId) {
        shortTermMemoryRedisCacheService.markConversationUpdated(conversationId);
    }
}
