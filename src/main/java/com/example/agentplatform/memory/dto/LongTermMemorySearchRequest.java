package com.example.agentplatform.memory.dto;

import com.example.agentplatform.memory.domain.MemoryType;

import java.util.List;

/**
 * 长期记忆查询请求。
 * 按当前登录用户维度查询不同类别的稳定事实。
 */
public record LongTermMemorySearchRequest(
        List<MemoryType> memoryTypes,
        String subject,
        Integer minImportance,
        Boolean active,
        Integer limit,
        MemoryMetadataFilter metadataFilter
) {
}
