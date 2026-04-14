package com.example.agentplatform.memory.domain;

import java.util.List;

/**
 * 统一记忆上下文。
 * 按固定顺序聚合最近会话消息、长期稳定事实和相关摘要。
 */
public record MemoryContext(
        List<RecentConversationMessage> recentMessages,
        List<LongTermMemory> stableFacts,
        List<RetrievedMemorySummary> recalledSummaries,
        String renderedContext
) {
}
