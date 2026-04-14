package com.example.agentplatform.document.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.document.domain.KnowledgeChunk;
import com.example.agentplatform.document.repository.KnowledgeChunkRepository;
import com.example.agentplatform.rag.service.EmbeddingService;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务分片写入器。
 * 负责把 ETL 产出的 chunk 文档写入业务表，保留 FTS 和来源追踪能力。
 */
@Component
public class KnowledgeChunkDocumentWriter implements DocumentWriter {

    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final EmbeddingService embeddingService;

    public KnowledgeChunkDocumentWriter(
            KnowledgeChunkRepository knowledgeChunkRepository,
            EmbeddingService embeddingService
    ) {
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * 把 chunk 文档写入业务分片表。
     */
    @Override
    public void accept(List<Document> documents) {
        write(documents);
    }

    /**
     * 把 chunk 文档写入业务分片表。
     */
    @Override
    public void write(List<Document> documents) {
        List<KnowledgeChunk> chunks = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            Long documentId = readLong(metadata, DocumentMetadataKeys.DOCUMENT_ID);
            Integer chunkIndex = readInteger(metadata, DocumentMetadataKeys.CHUNK_INDEX);
            chunks.add(new KnowledgeChunk(
                    null,
                    documentId,
                    chunkIndex,
                    document.getText(),
                    embeddingService.embed(document.getText(), "document-ingest"),
                    estimateTokenCount(document.getText()),
                    metadata,
                    null
            ));
        }
        List<KnowledgeChunk> persistedChunks = knowledgeChunkRepository.saveAll(chunks);
        for (int index = 0; index < persistedChunks.size(); index++) {
            Document document = documents.get(index);
            KnowledgeChunk persistedChunk = persistedChunks.get(index);
            documents.set(index, document.mutate()
                    .metadata(DocumentMetadataKeys.CHUNK_ID, persistedChunk.id())
                    .build());
        }
    }

    private Long readLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new ApplicationException("Missing required metadata: " + key);
    }

    private Integer readInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        throw new ApplicationException("Missing required metadata: " + key);
    }

    private int estimateTokenCount(String content) {
        return Math.max(1, content.split("\\s+").length);
    }
}
