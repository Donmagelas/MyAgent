package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemorySummary;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import com.example.agentplatform.memory.dto.MemorySummaryWriteRequest;

import java.util.List;

/**
 * 记忆摘要服务。
 * 提供摘要写入与语义召回能力。
 */
public interface MemorySummaryService {

    /** 写入一条带 embedding 的记忆摘要。 */
    MemorySummary save(MemorySummaryWriteRequest request);

    /** 基于当前问题检索相关摘要。 */
    List<RetrievedMemorySummary> search(Long userId, String question, int topK, MemoryMetadataFilter metadataFilter);
}
