package com.example.agentplatform.memory.dto;

import com.example.agentplatform.memory.domain.MemoryType;

import java.util.List;

/**
 * 长期记忆查询请求。
 * 支持按用户、类型、主题、重要度和生效状态过滤。
 */
public record LongTermMemoryQueryRequest(
        Long userId,
        List<MemoryType> memoryTypes,
        String subject,
        Integer minImportance,
        Boolean active,
        Integer limit,
        MemoryMetadataFilter metadataFilter
) {
}
