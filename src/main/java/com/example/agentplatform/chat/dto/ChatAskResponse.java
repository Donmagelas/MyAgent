package com.example.agentplatform.chat.dto;

import java.util.List;

/**
 * 聊天响应对象。
 * 包含最终回答以及可选的 grounding 来源。
 */
public record ChatAskResponse(
        Long conversationId,
        String sessionId,
        String answer,
        List<SourceItem> sources
) {
    /** grounded answer 场景返回的轻量来源视图。 */
    public record SourceItem(
            Long documentId,
            String documentTitle,
            String chunkTitle,
            String sectionPath,
            String jsonPath,
            Long chunkId,
            int chunkIndex,
            String retrievalType,
            double score
    ) {
    }
}