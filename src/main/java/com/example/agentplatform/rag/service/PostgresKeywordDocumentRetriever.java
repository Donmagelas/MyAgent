package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHybridRetrievalProperties;
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
 * 基于 PostgreSQL FTS 的关键词检索器。
 * 负责把知识分片表中的全文检索结果转换为 Spring AI Document。
 */
@Component
public class PostgresKeywordDocumentRetriever implements DocumentRetriever {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;
    private final RagProperties ragProperties;
    private final RagHybridRetrievalProperties ragHybridRetrievalProperties;
    private final PostgresFtsQueryBuilder postgresFtsQueryBuilder;

    public PostgresKeywordDocumentRetriever(
            KnowledgeChunkRepository knowledgeChunkRepository,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper,
            RagProperties ragProperties,
            RagHybridRetrievalProperties ragHybridRetrievalProperties,
            PostgresFtsQueryBuilder postgresFtsQueryBuilder
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
        this.ragProperties = ragProperties;
        this.ragHybridRetrievalProperties = ragHybridRetrievalProperties;
        this.postgresFtsQueryBuilder = postgresFtsQueryBuilder;
    }

    /**
     * 使用 FTS 检索关键词命中的文档。
     */
    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || !StringUtils.hasText(query.text()) || !ragHybridRetrievalProperties.keywordEnabled()) {
            return List.of();
        }
        String tsQuery = postgresFtsQueryBuilder.build(query.text());
        if (!StringUtils.hasText(tsQuery)) {
            return List.of();
        }
        List<RetrievedChunk> chunks = knowledgeChunkRepository.keywordSearch(
                tsQuery,
                ragHybridRetrievalProperties.keywordTsConfig(),
                ragProperties.keywordMinScore(),
                ragProperties.keywordTopK()
        ).stream().map(chunk -> chunk.withRetrievalType("keyword")).toList();
        return springAiRetrievedDocumentMapper.toDocuments(chunks);
    }
}
