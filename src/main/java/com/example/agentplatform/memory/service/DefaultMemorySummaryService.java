package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemorySummary;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import com.example.agentplatform.memory.dto.MemorySummaryWriteRequest;
import com.example.agentplatform.memory.repository.MemorySummaryRepository;
import com.example.agentplatform.rag.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认记忆摘要服务实现。
 * 调用 embedding 模型生成向量，再委托仓储完成写入和检索。
 */
@Service
public class DefaultMemorySummaryService implements MemorySummaryService {

    private final MemorySummaryRepository memorySummaryRepository;
    private final EmbeddingService embeddingService;

    public DefaultMemorySummaryService(
            MemorySummaryRepository memorySummaryRepository,
            EmbeddingService embeddingService
    ) {
        this.memorySummaryRepository = memorySummaryRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public MemorySummary save(MemorySummaryWriteRequest request) {
        float[] embedding = embeddingService.embed(request.summaryText(), "memory-summary-write");
        return memorySummaryRepository.save(request, embedding);
    }

    @Override
    public List<RetrievedMemorySummary> search(Long userId, String question, int topK, MemoryMetadataFilter metadataFilter) {
        float[] embedding = embeddingService.embed(question, "memory-summary-search");
        return memorySummaryRepository.semanticSearch(userId, embedding, topK, metadataFilter);
    }
}
