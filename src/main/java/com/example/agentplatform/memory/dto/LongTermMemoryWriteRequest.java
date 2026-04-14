package com.example.agentplatform.memory.dto;

import com.example.agentplatform.memory.domain.MemoryType;

import java.util.Map;

/**
 * 长期记忆写入请求。
 * 只接收提炼后的稳定事实，不接收原始聊天全文。
 */
public record LongTermMemoryWriteRequest(
        Long userId,
        Long conversationId,
        MemoryType memoryType,
        String subject,
        String content,
        Integer importance,
        Boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata
) {
}
