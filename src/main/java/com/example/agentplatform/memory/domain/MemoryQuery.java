package com.example.agentplatform.memory.domain;

import java.util.List;

/**
 * 记忆查询参数。
 * 统一描述回答前一次完整记忆上下文组装所需的输入。
 */
public record MemoryQuery(
        Long userId,
        Long conversationId,
        String question,
        Integer recentMessageLimit,
        Integer stableFactLimit,
        Integer summaryTopK,
        List<MemoryType> memoryTypes
) {
}
