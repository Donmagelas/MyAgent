package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagProperties;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 去重型文档合并器。
 * 用于多查询扩展后合并结果，避免同一 chunk 在最终上下文中重复出现。
 */
@Component
public class DeduplicatingDocumentJoiner implements DocumentJoiner {

    private final RagProperties ragProperties;

    public DeduplicatingDocumentJoiner(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    /**
     * 合并多查询结果并按得分去重排序。
     */
    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQueries) {
        Map<String, Document> deduplicatedDocuments = new LinkedHashMap<>();
        for (List<List<Document>> documentsPerQuery : documentsForQueries.values()) {
            for (List<Document> documents : documentsPerQuery) {
                for (Document document : documents) {
                    String identity = buildIdentity(document);
                    deduplicatedDocuments.compute(identity, (key, existing) -> merge(existing, document));
                }
            }
        }
        return deduplicatedDocuments.values().stream()
                .sorted((left, right) -> Double.compare(scoreOf(right), scoreOf(left)))
                .limit(ragProperties.maxContextChunks())
                .toList();
    }

    private Document merge(Document existing, Document incoming) {
        if (existing == null) {
            return incoming;
        }
        double existingScore = scoreOf(existing);
        double incomingScore = scoreOf(incoming);
        Document preferred = incomingScore > existingScore ? incoming : existing;
        Document secondary = preferred == incoming ? existing : incoming;

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (preferred.getMetadata() != null) {
            metadata.putAll(preferred.getMetadata());
        }
        metadata.put(DocumentMetadataKeys.RETRIEVAL_TYPE, mergeRetrievalTypes(preferred, secondary));
        return preferred.mutate()
                .metadata(metadata)
                .score(Math.max(existingScore, incomingScore))
                .build();
    }

    private String mergeRetrievalTypes(Document first, Document second) {
        String firstType = String.valueOf(first.getMetadata().getOrDefault(DocumentMetadataKeys.RETRIEVAL_TYPE, "vector"));
        String secondType = String.valueOf(second.getMetadata().getOrDefault(DocumentMetadataKeys.RETRIEVAL_TYPE, "vector"));
        return RetrievalTypeNormalizer.normalize(firstType, secondType);
    }

    private String buildIdentity(Document document) {
        Object chunkId = document.getMetadata().get(DocumentMetadataKeys.CHUNK_ID);
        if (chunkId != null) {
            return "chunk:" + chunkId;
        }
        Object documentId = document.getMetadata().get(DocumentMetadataKeys.DOCUMENT_ID);
        Object chunkIndex = document.getMetadata().get(DocumentMetadataKeys.CHUNK_INDEX);
        if (documentId != null && chunkIndex != null) {
            return "document:" + documentId + ":" + chunkIndex;
        }
        Object documentTitle = document.getMetadata().get(DocumentMetadataKeys.DOCUMENT_TITLE);
        if (documentTitle != null && chunkIndex != null) {
            return "title:" + documentTitle + ":" + chunkIndex;
        }
        if (documentTitle != null) {
            return "title-only:" + documentTitle;
        }
        Object sourceUri = document.getMetadata().get(DocumentMetadataKeys.SOURCE_URI);
        if (sourceUri != null && chunkIndex != null) {
            return "source:" + sourceUri + ":" + chunkIndex;
        }
        if (document.getText() != null) {
            return "content:" + Integer.toHexString(document.getText().hashCode());
        }
        return "doc:" + document.getId();
    }

    private double scoreOf(Document document) {
        return document.getScore() == null ? 0.0d : document.getScore();
    }
}
