package com.example.agentplatform.rag.domain;

import com.example.agentplatform.document.domain.DocumentMetadataKeys;

import java.util.Map;

/**
 * 检索结果项。
 * 包含命中的 chunk、分数以及检索策略元数据。
 */
public record RetrievedChunk(
        Long chunkId,
        Long documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        Map<String, Object> metadata,
        double score,
        String retrievalType
) {
    /** 返回一个附带检索策略标记的副本对象。 */
    public RetrievedChunk withRetrievalType(String retrievalType) {
        return new RetrievedChunk(chunkId, documentId, documentTitle, chunkIndex, content, metadata, score, retrievalType);
    }

    /** 返回 chunk 的增强标题，优先用于前端展示来源。 */
    public String chunkTitle() {
        return readMetadata(DocumentMetadataKeys.CHUNK_TITLE);
    }

    /** 返回 section 路径，适合展示分块所在章节。 */
    public String sectionPath() {
        return readMetadata(DocumentMetadataKeys.SECTION_PATH);
    }

    /** 返回 JSON 路径，适合展示字段级来源。 */
    public String jsonPath() {
        return readMetadata(DocumentMetadataKeys.JSON_PATH);
    }

    private String readMetadata(String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}