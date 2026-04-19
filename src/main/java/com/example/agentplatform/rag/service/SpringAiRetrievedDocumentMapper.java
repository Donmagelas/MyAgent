package com.example.agentplatform.rag.service;

import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.ResponseMetadata;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 文档检索结果映射器。
 * 负责把 Spring AI Document 和 Advisor 上下文转换为项目内部的检索结果对象。
 */
@Component
public class SpringAiRetrievedDocumentMapper {

    /**
     * 把 Spring AI 文档列表转换为内部检索结果。
     */
    public List<RetrievedChunk> toRetrievedChunks(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> chunks = new ArrayList<>(documents.size());
        for (Document document : documents) {
            chunks.add(toRetrievedChunk(document));
        }
        return chunks;
    }

    /**
     * 把内部检索结果转换为 Spring AI Document。
     */
    public List<Document> toDocuments(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream().map(this::toDocument).toList();
    }

    /**
     * 从 RetrievalAugmentationAdvisor 上下文中提取检索结果。
     */
    public List<RetrievedChunk> fromAdvisorContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        Object value = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return toRetrievedChunks(readDocuments(value));
    }

    /**
     * 从 ChatResponse metadata 中提取检索结果。
     */
    public List<RetrievedChunk> fromResponseMetadata(ResponseMetadata metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        Object value = metadata.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return toRetrievedChunks(readDocuments(value));
    }

    private List<Document> readDocuments(Object value) {
        if (!(value instanceof List<?> documentList) || documentList.isEmpty()) {
            return List.of();
        }
        return documentList.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
    }

    /**
     * 把检索结果转换为对外返回的来源列表。
     */
    public List<ChatAskResponse.SourceItem> toSourceItems(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> new ChatAskResponse.SourceItem(
                        chunk.documentId(),
                        chunk.documentTitle(),
                        chunk.chunkTitle(),
                        chunk.sectionPath(),
                        chunk.jsonPath(),
                        chunk.chunkId(),
                        chunk.chunkIndex(),
                        chunk.retrievalType(),
                        chunk.score()
                ))
                .toList();
    }

    /**
     * 使用预先检索出的候选结果补齐 Advisor 返回结果中的检索类型等元数据。
     */
    public List<RetrievedChunk> mergeWithFallback(List<RetrievedChunk> actualChunks, List<RetrievedChunk> fallbackChunks) {
        if (actualChunks == null || actualChunks.isEmpty()) {
            return fallbackChunks == null ? List.of() : fallbackChunks;
        }
        if (fallbackChunks == null || fallbackChunks.isEmpty()) {
            return actualChunks;
        }

        Map<String, RetrievedChunk> fallbackIndex = new LinkedHashMap<>();
        for (RetrievedChunk chunk : fallbackChunks) {
            fallbackIndex.put(buildChunkIdentity(chunk), chunk);
        }

        return actualChunks.stream()
                .map(actualChunk -> mergeChunk(actualChunk, fallbackIndex.get(buildChunkIdentity(actualChunk))))
                .toList();
    }

    private RetrievedChunk toRetrievedChunk(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new RetrievedChunk(
                readLong(metadata, DocumentMetadataKeys.CHUNK_ID),
                readLong(metadata, DocumentMetadataKeys.DOCUMENT_ID),
                readString(metadata, DocumentMetadataKeys.DOCUMENT_TITLE),
                readInteger(metadata, DocumentMetadataKeys.CHUNK_INDEX, 0),
                document.getText(),
                metadata,
                document.getScore() == null ? 0.0d : document.getScore(),
                RetrievalTypeNormalizer.normalize(readString(metadata, DocumentMetadataKeys.RETRIEVAL_TYPE, "vector"))
        );
    }

    private Document toDocument(RetrievedChunk chunk) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (chunk.metadata() != null) {
            metadata.putAll(chunk.metadata());
        }
        metadata.put(DocumentMetadataKeys.CHUNK_ID, chunk.chunkId());
        metadata.put(DocumentMetadataKeys.DOCUMENT_ID, chunk.documentId());
        metadata.put(DocumentMetadataKeys.DOCUMENT_TITLE, chunk.documentTitle());
        metadata.put(DocumentMetadataKeys.CHUNK_INDEX, chunk.chunkIndex());
        metadata.put(DocumentMetadataKeys.RETRIEVAL_TYPE, RetrievalTypeNormalizer.normalize(chunk.retrievalType()));
        return Document.builder()
                .id(buildDocumentId(chunk))
                .text(chunk.content())
                .metadata(metadata)
                .score(chunk.score())
                .build();
    }

    private Long readLong(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private int readInteger(Map<String, Object> metadata, String key, int defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }

    private String readString(Map<String, Object> metadata, String key) {
        return readString(metadata, key, "unknown");
    }

    private String readString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private String buildDocumentId(RetrievedChunk chunk) {
        if (chunk.chunkId() != null) {
            return "chunk-" + chunk.chunkId();
        }
        if (chunk.documentId() != null) {
            return "document-" + chunk.documentId() + "-" + chunk.chunkIndex();
        }
        return "retrieved-" + chunk.hashCode();
    }

    private RetrievedChunk mergeChunk(RetrievedChunk actualChunk, RetrievedChunk fallbackChunk) {
        if (fallbackChunk == null) {
            return actualChunk;
        }
        String mergedRetrievalType = mergeRetrievalTypes(actualChunk.retrievalType(), fallbackChunk.retrievalType());
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (fallbackChunk.metadata() != null) {
            mergedMetadata.putAll(fallbackChunk.metadata());
        }
        if (actualChunk.metadata() != null) {
            mergedMetadata.putAll(actualChunk.metadata());
        }
        mergedMetadata.put(DocumentMetadataKeys.RETRIEVAL_TYPE, mergedRetrievalType);
        return new RetrievedChunk(
                actualChunk.chunkId() == null ? fallbackChunk.chunkId() : actualChunk.chunkId(),
                actualChunk.documentId() == null ? fallbackChunk.documentId() : actualChunk.documentId(),
                "unknown".equals(actualChunk.documentTitle()) ? fallbackChunk.documentTitle() : actualChunk.documentTitle(),
                actualChunk.chunkIndex(),
                actualChunk.content(),
                mergedMetadata,
                actualChunk.score(),
                mergedRetrievalType
        );
    }

    private String buildChunkIdentity(RetrievedChunk chunk) {
        if (chunk.chunkId() != null) {
            return "chunk:" + chunk.chunkId();
        }
        if (chunk.documentId() != null) {
            return "document:" + chunk.documentId() + ":" + chunk.chunkIndex();
        }
        return "title:" + chunk.documentTitle() + ":" + chunk.chunkIndex();
    }

    private String mergeRetrievalTypes(String left, String right) {
        return RetrievalTypeNormalizer.normalize(left, right);
    }
}
