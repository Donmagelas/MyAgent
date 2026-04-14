package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHybridRetrievalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 混合检索器。
 * 先执行向量检索和关键词检索，再融合并调用 rerank 输出最终候选集。
 */
@Component
public class HybridDocumentRetriever {

    private static final String ORIGINAL_QUERY_KEY = "originalQuery";
    private static final Logger log = LoggerFactory.getLogger(HybridDocumentRetriever.class);

    private final RagHybridRetrievalProperties ragHybridRetrievalProperties;
    private final PostgresKeywordDocumentRetriever postgresKeywordDocumentRetriever;
    private final HybridDocumentFusionService hybridDocumentFusionService;
    private final DashScopeDocumentRerankService dashScopeDocumentRerankService;

    public HybridDocumentRetriever(
            RagHybridRetrievalProperties ragHybridRetrievalProperties,
            PostgresKeywordDocumentRetriever postgresKeywordDocumentRetriever,
            HybridDocumentFusionService hybridDocumentFusionService,
            DashScopeDocumentRerankService dashScopeDocumentRerankService
    ) {
        this.ragHybridRetrievalProperties = ragHybridRetrievalProperties;
        this.postgresKeywordDocumentRetriever = postgresKeywordDocumentRetriever;
        this.hybridDocumentFusionService = hybridDocumentFusionService;
        this.dashScopeDocumentRerankService = dashScopeDocumentRerankService;
    }

    /**
     * 使用传入的向量检索器执行完整混合检索流程。
     */
    public List<Document> retrieve(Query query, DocumentRetriever vectorDocumentRetriever) {
        if (query == null || !StringUtils.hasText(query.text())) {
            return List.of();
        }
        Long workflowId = extractWorkflowId(query);
        List<Document> vectorDocuments = vectorDocumentRetriever.retrieve(query);
        if (!ragHybridRetrievalProperties.enabled()) {
            return dashScopeDocumentRerankService.rerank(query.text(), vectorDocuments, workflowId);
        }
        Query keywordQuery = buildKeywordQuery(query);
        List<Document> keywordDocuments = postgresKeywordDocumentRetriever.retrieve(keywordQuery);
        List<Document> fusedDocuments = hybridDocumentFusionService.fuse(vectorDocuments, keywordDocuments);
        log.debug("混合检索计数 query='{}' keywordQuery='{}' vector={} keyword={} fused={}",
                query.text(),
                keywordQuery.text(),
                vectorDocuments.size(),
                keywordDocuments.size(),
                fusedDocuments.size());
        return dashScopeDocumentRerankService.rerank(query.text(), fusedDocuments, workflowId);
    }

    private Query buildKeywordQuery(Query query) {
        if (query.context() == null) {
            return query;
        }
        Object originalQuery = query.context().get(ORIGINAL_QUERY_KEY);
        if (!(originalQuery instanceof String originalText) || !StringUtils.hasText(originalText)) {
            return query;
        }
        return Query.builder()
                .text(originalText.trim())
                .history(query.history())
                .context(query.context())
                .build();
    }

    /**
     * 从 Query.context 中提取 workflowId，用于把 rerank usage 归集到当前工作流。
     */
    private Long extractWorkflowId(Query query) {
        if (query.context() == null) {
            return null;
        }
        Object value = query.context().get(RetrievalService.WORKFLOW_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
