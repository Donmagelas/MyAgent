package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHybridRetrievalProperties;
import com.example.agentplatform.config.RagProperties;
import com.example.agentplatform.document.repository.KnowledgeChunkRepository;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轻量 RAG 探测检索测试。
 * 确认 probe 只使用原始向量召回和 FTS 召回，并按原始分数去重统计。
 */
@ExtendWith(MockitoExtension.class)
class RagProbeRetrievalServiceTest {

    @Mock
    private KnowledgeChunkRepository knowledgeChunkRepository;
    @Mock
    private SpringAiEmbeddingService springAiEmbeddingService;
    @Mock
    private PostgresFtsQueryBuilder postgresFtsQueryBuilder;

    private RagProbeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new RagProbeRetrievalService(
                knowledgeChunkRepository,
                springAiEmbeddingService,
                postgresFtsQueryBuilder,
                new RagProperties(8, 8, 6, 0.50d, 0.10d, 800, 120),
                new RagHybridRetrievalProperties(true, true, true, "simple", 8, 5, 0.6d, 0.4d)
        );
    }

    @Test
    void shouldProbeWithRawVectorAndKeywordSearchOnly() {
        float[] embedding = new float[]{0.1f, 0.2f};
        when(springAiEmbeddingService.embed("哈基米 含义", "rag-probe-vector-query", 10L)).thenReturn(embedding);
        when(knowledgeChunkRepository.vectorSearch(embedding, 8)).thenReturn(List.of(
                chunk(1L, 0.72d, "unknown"),
                chunk(2L, 0.45d, "unknown")
        ));
        when(postgresFtsQueryBuilder.build("哈基米 含义")).thenReturn("哈基米:* | 含义:*");
        when(knowledgeChunkRepository.keywordSearch("哈基米:* | 含义:*", "simple", 0.10d, 8)).thenReturn(List.of(
                chunk(1L, 0.20d, "unknown"),
                chunk(3L, 0.30d, "unknown")
        ));

        RagProbeRetrievalService.ProbeResult result = service.probe("哈基米 含义", 10L);

        assertThat(result.hitCount()).isEqualTo(2);
        assertThat(result.vectorHitCount()).isEqualTo(1);
        assertThat(result.keywordHitCount()).isEqualTo(2);
        assertThat(result.topScore()).isEqualTo(0.72d);
        assertThat(result.chunks())
                .extracting(RetrievedChunk::chunkId)
                .containsExactly(1L, 3L);
        verify(springAiEmbeddingService).embed("哈基米 含义", "rag-probe-vector-query", 10L);
        verify(knowledgeChunkRepository).vectorSearch(eq(embedding), eq(8));
        verify(knowledgeChunkRepository).keywordSearch("哈基米:* | 含义:*", "simple", 0.10d, 8);
    }

    private RetrievedChunk chunk(Long chunkId, double score, String retrievalType) {
        return new RetrievedChunk(
                chunkId,
                100L,
                "doc.md",
                chunkId.intValue(),
                "content-" + chunkId,
                Map.of(),
                score,
                retrievalType
        );
    }
}
