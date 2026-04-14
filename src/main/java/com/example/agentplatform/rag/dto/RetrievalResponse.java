package com.example.agentplatform.rag.dto;

import java.util.List;

/**
 * 检索接口响应对象。
 * 返回原始查询和命中的分片列表。
 */
public record RetrievalResponse(
        String query,
        List<RetrievedChunkItem> chunks
) {
    /** 提供给接口调用方的轻量检索结果视图。 */
    public record RetrievedChunkItem(
            Long chunkId,
            Long documentId,
            String documentTitle,
            String chunkTitle,
            String sectionPath,
            String jsonPath,
            int chunkIndex,
            String content,
            double score,
            String retrievalType
    ) {
    }
}