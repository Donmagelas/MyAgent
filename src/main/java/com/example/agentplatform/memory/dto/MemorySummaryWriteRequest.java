package com.example.agentplatform.memory.dto;

import java.util.Map;

/**
 * 记忆摘要写入请求。
 * 摘要层只保存可检索的压缩内容，不与长期记忆正文混用。
 */
public record MemorySummaryWriteRequest(
        Long userId,
        Long longTermMemoryId,
        Long conversationId,
        String summaryText,
        Integer importance,
        Boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata
) {
}
