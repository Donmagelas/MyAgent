package com.example.agentplatform.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 文档检索执行器。
 * 负责在独立检索接口中复用与 Advisor 一致的查询增强、扩展和文档合并流程。
 */
final class SpringAiRagDocumentRetrievalExecutor {

    private static final String ORIGINAL_QUERY_KEY = "originalQuery";

    private final DocumentRetriever documentRetriever;
    private final DocumentJoiner documentJoiner;
    private final SpringAiRagQueryEnhancementService ragQueryEnhancementService;
    private final int maxContextChunks;
    private final Long workflowId;

    SpringAiRagDocumentRetrievalExecutor(
            DocumentRetriever documentRetriever,
            DocumentJoiner documentJoiner,
            SpringAiRagQueryEnhancementService ragQueryEnhancementService,
            int maxContextChunks,
            Long workflowId
    ) {
        this.documentRetriever = documentRetriever;
        this.documentJoiner = documentJoiner;
        this.ragQueryEnhancementService = ragQueryEnhancementService;
        this.maxContextChunks = maxContextChunks;
        this.workflowId = workflowId;
    }

    /**
     * 执行完整检索流程。
     */
    List<Document> retrieve(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        String normalizedQueryText = queryText.trim();
        Query originalQuery = Query.builder()
                .text(normalizedQueryText)
                .context(buildInitialContext(normalizedQueryText))
                .build();
        Query transformedQuery = withOriginalQuery(
                ragQueryEnhancementService.transform(originalQuery),
                normalizedQueryText
        );
        List<Query> expandedQueries = ragQueryEnhancementService.expand(transformedQuery).stream()
                .map(query -> withOriginalQuery(query, normalizedQueryText))
                .toList();

        Map<Query, List<List<Document>>> documentsForQueries = new LinkedHashMap<>();
        for (Query expandedQuery : expandedQueries) {
            if (expandedQuery == null || !StringUtils.hasText(expandedQuery.text())) {
                continue;
            }
            documentsForQueries.put(expandedQuery, List.of(documentRetriever.retrieve(expandedQuery)));
        }
        if (documentsForQueries.isEmpty()) {
            return List.of();
        }

        return documentJoiner.join(documentsForQueries).stream()
                .limit(maxContextChunks)
                .toList();
    }

    private Query withOriginalQuery(Query query, String originalQuery) {
        if (query == null) {
            return null;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        if (query.context() != null && !query.context().isEmpty()) {
            context.putAll(query.context());
        }
        context.put(ORIGINAL_QUERY_KEY, originalQuery);
        if (workflowId != null) {
            context.put(RetrievalService.WORKFLOW_ID_CONTEXT_KEY, workflowId);
        }
        return Query.builder()
                .text(query.text())
                .history(query.history())
                .context(context)
                .build();
    }

    /**
     * 构建初始检索上下文，确保原始 query 和 workflowId 都能继续向后透传。
     */
    private Map<String, Object> buildInitialContext(String originalQuery) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(ORIGINAL_QUERY_KEY, originalQuery);
        if (workflowId != null) {
            context.put(RetrievalService.WORKFLOW_ID_CONTEXT_KEY, workflowId);
        }
        return context;
    }
}
