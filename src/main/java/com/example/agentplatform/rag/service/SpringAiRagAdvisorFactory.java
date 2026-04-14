package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI RAG 组件工厂。
 * 统一创建查询增强、混合检索、结果合并和 RetrievalAugmentationAdvisor。
 */
@Component
public class SpringAiRagAdvisorFactory {

    private final RagProperties ragProperties;
    private final SpringAiRagQueryEnhancementService ragQueryEnhancementService;
    private final KnowledgeChunkVectorDocumentRetriever knowledgeChunkVectorDocumentRetriever;
    private final HybridDocumentRetriever hybridDocumentRetriever;
    private final DeduplicatingDocumentJoiner deduplicatingDocumentJoiner;

    public SpringAiRagAdvisorFactory(
            RagProperties ragProperties,
            SpringAiRagQueryEnhancementService ragQueryEnhancementService,
            KnowledgeChunkVectorDocumentRetriever knowledgeChunkVectorDocumentRetriever,
            HybridDocumentRetriever hybridDocumentRetriever,
            DeduplicatingDocumentJoiner deduplicatingDocumentJoiner
    ) {
        this.ragProperties = ragProperties;
        this.ragQueryEnhancementService = ragQueryEnhancementService;
        this.knowledgeChunkVectorDocumentRetriever = knowledgeChunkVectorDocumentRetriever;
        this.hybridDocumentRetriever = hybridDocumentRetriever;
        this.deduplicatingDocumentJoiner = deduplicatingDocumentJoiner;
    }

    /**
     * 创建向量检索器。
     * 向量检索继续复用 Spring AI 的 VectorStoreDocumentRetriever。
     */
    public DocumentRetriever createVectorDocumentRetriever() {
        return knowledgeChunkVectorDocumentRetriever;
    }

    /**
     * 创建混合检索器。
     * 先向量召回，再结合 PostgreSQL FTS、融合和 rerank 输出最终候选集。
     */
    public DocumentRetriever createDocumentRetriever() {
        DocumentRetriever vectorDocumentRetriever = createVectorDocumentRetriever();
        return query -> hybridDocumentRetriever.retrieve(query, vectorDocumentRetriever);
    }

    /**
     * 创建多查询结果合并器。
     */
    public DocumentJoiner createDocumentJoiner() {
        return deduplicatingDocumentJoiner;
    }

    /**
     * 创建完整的 RetrievalAugmentationAdvisor。
     */
    public RetrievalAugmentationAdvisor createRetrievalAugmentationAdvisor() {
        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(createDocumentRetriever())
                .documentJoiner(createDocumentJoiner())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build());

        List<QueryTransformer> queryTransformers = ragQueryEnhancementService.getQueryTransformers();
        if (!queryTransformers.isEmpty()) {
            builder.queryTransformers(queryTransformers);
        }

        QueryExpander queryExpander = ragQueryEnhancementService.getQueryExpander();
        if (queryExpander != null) {
            builder.queryExpander(queryExpander);
        }

        return builder.build();
    }

    /**
     * 供独立检索接口复用完整混合检索链。
     */
    public List<Document> retrieveDocuments(String queryText) {
        return retrieveDocuments(queryText, null);
    }

    public List<Document> retrieveDocuments(String queryText, Long workflowId) {
        return new SpringAiRagDocumentRetrievalExecutor(
                createDocumentRetriever(),
                createDocumentJoiner(),
                ragQueryEnhancementService,
                ragProperties.maxContextChunks(),
                workflowId
        ).retrieve(queryText);
    }
}
