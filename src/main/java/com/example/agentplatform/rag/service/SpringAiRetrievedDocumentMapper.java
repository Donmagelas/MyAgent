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
 * Spring AI 鏂囨。妫€绱㈢粨鏋滄槧灏勫櫒銆? * 璐熻矗鎶?Spring AI Document 鍜?Advisor 涓婁笅鏂囪浆鎹负椤圭洰鍐呴儴鐨勬绱㈢粨鏋滃璞°€? */
@Component
public class SpringAiRetrievedDocumentMapper {

    /**
     * 鎶?Spring AI 鏂囨。鍒楄〃杞崲涓哄唴閮ㄦ绱㈢粨鏋溿€?     */
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
     * 鎶婂唴閮ㄦ绱㈢粨鏋滆浆鎹负 Spring AI Document銆?     */
    public List<Document> toDocuments(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream().map(this::toDocument).toList();
    }

    /**
     * 浠?RetrievalAugmentationAdvisor 涓婁笅鏂囦腑鎻愬彇妫€绱㈢粨鏋溿€?     */
    public List<RetrievedChunk> fromAdvisorContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        Object value = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return toRetrievedChunks(readDocuments(value));
    }

    /**
     * 浠?ChatResponse metadata 涓彁鍙栨绱㈢粨鏋溿€?     */
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
     * 鎶婃绱㈢粨鏋滆浆鎹负瀵瑰杩斿洖鐨勬潵婧愬垪琛ㄣ€?     */
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
     * 浣跨敤棰勫厛妫€绱㈠嚭鐨勫€欓€夌粨鏋滆ˉ榻?Advisor 杩斿洖缁撴灉涓殑妫€绱㈢被鍨嬬瓑鍏冩暟鎹€?     */
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
