package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHybridRetrievalProperties;
import com.example.agentplatform.config.RagProperties;
import com.example.agentplatform.document.repository.KnowledgeChunkRepository;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 入口判断专用的轻量检索探测服务。
 * 只执行原始向量召回和 PostgreSQL FTS 召回，不做 query rewrite、multi-query、融合加权或 rerank。
 */
@Service
public class RagProbeRetrievalService {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final SpringAiEmbeddingService springAiEmbeddingService;
    private final PostgresFtsQueryBuilder postgresFtsQueryBuilder;
    private final RagProperties ragProperties;
    private final RagHybridRetrievalProperties ragHybridRetrievalProperties;

    public RagProbeRetrievalService(
            KnowledgeChunkRepository knowledgeChunkRepository,
            SpringAiEmbeddingService springAiEmbeddingService,
            PostgresFtsQueryBuilder postgresFtsQueryBuilder,
            RagProperties ragProperties,
            RagHybridRetrievalProperties ragHybridRetrievalProperties
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.springAiEmbeddingService = springAiEmbeddingService;
        this.postgresFtsQueryBuilder = postgresFtsQueryBuilder;
        this.ragProperties = ragProperties;
        this.ragHybridRetrievalProperties = ragHybridRetrievalProperties;
    }

    /**
     * 执行轻量探测并返回去重后的原始召回统计。
     */
    public ProbeResult probe(String query, Long workflowId) {
        if (!StringUtils.hasText(query)) {
            return ProbeResult.empty();
        }

        String normalizedQuery = query.trim();
        List<RetrievedChunk> vectorChunks = retrieveVectorChunks(normalizedQuery, workflowId);
        List<RetrievedChunk> keywordChunks = retrieveKeywordChunks(normalizedQuery);
        List<RetrievedChunk> deduplicatedChunks = deduplicateByRawScore(vectorChunks, keywordChunks);

        double topScore = deduplicatedChunks.stream()
                .map(RetrievedChunk::score)
                .max(Comparator.naturalOrder())
                .orElse(0.0d);
        return new ProbeResult(
                deduplicatedChunks,
                deduplicatedChunks.size(),
                topScore,
                vectorChunks.size(),
                keywordChunks.size()
        );
    }

    private List<RetrievedChunk> retrieveVectorChunks(String query, Long workflowId) {
        float[] embedding = springAiEmbeddingService.embed(query, "rag-probe-vector-query", workflowId);
        return knowledgeChunkRepository.vectorSearch(embedding, ragProperties.vectorTopK()).stream()
                .filter(chunk -> chunk.score() >= ragProperties.similarityThreshold())
                .map(chunk -> chunk.withRetrievalType("probe-vector"))
                .toList();
    }

    private List<RetrievedChunk> retrieveKeywordChunks(String query) {
        if (!ragHybridRetrievalProperties.keywordEnabled()) {
            return List.of();
        }
        String tsQuery = postgresFtsQueryBuilder.build(query);
        if (!StringUtils.hasText(tsQuery)) {
            return List.of();
        }
        return knowledgeChunkRepository.keywordSearch(
                        tsQuery,
                        ragHybridRetrievalProperties.keywordTsConfig(),
                        ragProperties.keywordMinScore(),
                        ragProperties.keywordTopK()
                ).stream()
                .map(chunk -> chunk.withRetrievalType("probe-keyword"))
                .toList();
    }

    private List<RetrievedChunk> deduplicateByRawScore(
            List<RetrievedChunk> vectorChunks,
            List<RetrievedChunk> keywordChunks
    ) {
        Map<String, RetrievedChunk> candidates = new LinkedHashMap<>();
        mergeByRawScore(candidates, vectorChunks);
        mergeByRawScore(candidates, keywordChunks);
        return new ArrayList<>(candidates.values()).stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
    }

    private void mergeByRawScore(Map<String, RetrievedChunk> candidates, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (RetrievedChunk chunk : chunks) {
            String identity = buildIdentity(chunk);
            candidates.compute(identity, (key, existing) -> pickHigherRawScore(existing, chunk));
        }
    }

    private RetrievedChunk pickHigherRawScore(RetrievedChunk existing, RetrievedChunk incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming.score() > existing.score()) {
            return incoming.withRetrievalType(mergeRetrievalType(incoming.retrievalType(), existing.retrievalType()));
        }
        return existing.withRetrievalType(mergeRetrievalType(existing.retrievalType(), incoming.retrievalType()));
    }

    private String buildIdentity(RetrievedChunk chunk) {
        if (chunk.chunkId() != null) {
            return "chunk:" + chunk.chunkId();
        }
        if (chunk.documentId() != null) {
            return "document:" + chunk.documentId() + ":" + chunk.chunkIndex();
        }
        return "content:" + Integer.toHexString((chunk.content() == null ? "" : chunk.content()).hashCode());
    }

    private String mergeRetrievalType(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second) || first.equals(second)) {
            return first;
        }
        return first + "+" + second;
    }

    /**
     * 探测结果统计。chunks 保留原始召回分，不做融合加权。
     */
    public record ProbeResult(
            List<RetrievedChunk> chunks,
            int hitCount,
            double topScore,
            int vectorHitCount,
            int keywordHitCount
    ) {
        public static ProbeResult empty() {
            return new ProbeResult(List.of(), 0, 0.0d, 0, 0);
        }
    }
}
