package com.example.agentplatform.memory.advisor;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.memory.domain.MemoryQuery;
import com.example.agentplatform.memory.service.MemoryManager;
import org.springframework.stereotype.Service;

/**
 * 记忆上下文 advisor。
 * 在 chat 或 rag 执行前统一装配三层记忆上下文。
 */
@Service
public class MemoryContextAdvisor {

    private final MemoryManager memoryManager;
    private final MemoryProperties memoryProperties;

    public MemoryContextAdvisor(MemoryManager memoryManager, MemoryProperties memoryProperties) {
        this.memoryManager = memoryManager;
        this.memoryProperties = memoryProperties;
    }

    /** 基于当前用户、会话和问题构建可复用记忆上下文。 */
    public MemoryContext buildContext(Long userId, Long conversationId, String question) {
        return memoryManager.buildContext(new MemoryQuery(
                userId,
                conversationId,
                question,
                memoryProperties.shortTermWindowSize(),
                memoryProperties.stableFactLimit(),
                memoryProperties.summaryTopK(),
                null
        ));
    }
}
