package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagProperties;
import com.example.agentplatform.document.repository.KnowledgeChunkRepository;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 基于 knowledge_chunk 的向量检索器。
 * 负责使用 PostgreSQL pgvector 执行向量召回，并按阈值过滤 topK 结果。
 */
@Component
public class KnowledgeChunkVectorDocumentRetriever implements DocumentRetriever {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;
    private final SpringAiEmbeddingService springAiEmbeddingService;
    private final RagProperties ragProperties;

    public KnowledgeChunkVectorDocumentRetriever(
            KnowledgeChunkRepository knowledgeChunkRepository,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper,
            SpringAiEmbeddingService springAiEmbeddingService,
            RagProperties ragProperties
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
        this.springAiEmbeddingService = springAiEmbeddingService;
        this.ragProperties = ragProperties;
    }

    /**
     * 从 knowledge_chunk 中执行一次向量检索，并把结果转换成 Spring AI Document。
     */
    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || !StringUtils.hasText(query.text())) {
            return List.of();
        }
        float[] embedding = springAiEmbeddingService.embed(
                query.text().trim(),
                "rag-vector-query",
                extractWorkflowId(query)
        );
        List<RetrievedChunk> chunks = knowledgeChunkRepository.vectorSearch(embedding, ragProperties.vectorTopK()).stream()
                .filter(chunk -> chunk.score() >= ragProperties.similarityThreshold())
                .map(chunk -> chunk.withRetrievalType("vector"))
                .toList();
        return springAiRetrievedDocumentMapper.toDocuments(chunks);
    }

    /**
     * 从检索上下文中提取 workflowId，供 embedding usage 记录归集使用。
     */
    private Long extractWorkflowId(Query query) {
        if (query.context() == null) {
            return null;
        }
        Object value = query.context().get(RetrievalService.WORKFLOW_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
