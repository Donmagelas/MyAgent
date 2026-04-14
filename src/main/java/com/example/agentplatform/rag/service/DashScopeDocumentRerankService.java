package com.example.agentplatform.rag.service;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.RagHybridRetrievalProperties;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.service.ModelUsageLogService;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 DashScope RerankModel 的文档重排服务。
 * 使用 qwen3-vl-rerank 对融合候选集进行最终重排。
 */
@Service
public class DashScopeDocumentRerankService {

    private final RerankModel rerankModel;
    private final AiModelProperties aiModelProperties;
    private final RagHybridRetrievalProperties ragHybridRetrievalProperties;
    private final ModelUsageLogService modelUsageLogService;

    public DashScopeDocumentRerankService(
            RerankModel rerankModel,
            AiModelProperties aiModelProperties,
            RagHybridRetrievalProperties ragHybridRetrievalProperties,
            ModelUsageLogService modelUsageLogService
    ) {
        this.rerankModel = rerankModel;
        this.aiModelProperties = aiModelProperties;
        this.ragHybridRetrievalProperties = ragHybridRetrievalProperties;
        this.modelUsageLogService = modelUsageLogService;
    }

    /**
     * 对融合候选集执行 rerank。
     */
    public List<Document> rerank(String query, List<Document> documents) {
        return rerank(query, documents, null);
    }

    /**
     * 对融合候选集执行 rerank，并把 workflowId 写入 usage 记录。
     */
    public List<Document> rerank(String query, List<Document> documents, Long workflowId) {
        if (!ragHybridRetrievalProperties.rerankEnabled() || !StringUtils.hasText(query) || documents == null || documents.size() <= 1) {
            return trim(documents);
        }

        Instant start = Instant.now();
        try {
            DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                    .withModel(aiModelProperties.rerankModel())
                    .withTopN(Math.min(ragHybridRetrievalProperties.rerankTopN(), documents.size()))
                    .withReturnDocuments(true)
                    .build();
            RerankResponse response = rerankModel.call(new RerankRequest(query, documents, options));
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            recordUsage(response, latencyMs, true, null, workflowId);
            return trim(toDocuments(response, documents));
        }
        catch (Exception exception) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            recordUsage(null, latencyMs, false, exception.getMessage(), workflowId);
            return trim(documents);
        }
    }

    private List<Document> toDocuments(RerankResponse response, List<Document> originalDocuments) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return List.of();
        }
        Map<String, Document> originalDocumentIndex = indexOriginalDocuments(originalDocuments);
        return response.getResults().stream()
                .map(documentWithScore -> toDocument(documentWithScore, originalDocumentIndex))
                .toList();
    }

    private Document toDocument(DocumentWithScore documentWithScore, Map<String, Document> originalDocumentIndex) {
        Document rerankedDocument = documentWithScore.getOutput();
        Document document = resolveOriginalDocument(rerankedDocument, originalDocumentIndex);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put(DocumentMetadataKeys.RERANK_SCORE, documentWithScore.getScore());
        String retrievalType = String.valueOf(metadata.getOrDefault(DocumentMetadataKeys.RETRIEVAL_TYPE, "hybrid"));
        metadata.put(DocumentMetadataKeys.RETRIEVAL_TYPE, RetrievalTypeNormalizer.normalize(retrievalType, "rerank"));
        return document.mutate()
                .metadata(metadata)
                .score(documentWithScore.getScore())
                .build();
    }

    private Map<String, Document> indexOriginalDocuments(List<Document> originalDocuments) {
        Map<String, Document> indexedDocuments = new HashMap<>();
        if (originalDocuments == null) {
            return indexedDocuments;
        }
        for (Document document : originalDocuments) {
            for (String lookupKey : buildDocumentLookupKeys(document)) {
                indexedDocuments.putIfAbsent(lookupKey, document);
            }
        }
        return indexedDocuments;
    }

    private Document resolveOriginalDocument(Document rerankedDocument, Map<String, Document> originalDocumentIndex) {
        if (rerankedDocument == null) {
            return null;
        }
        for (String lookupKey : buildDocumentLookupKeys(rerankedDocument)) {
            Document originalDocument = originalDocumentIndex.get(lookupKey);
            if (originalDocument != null) {
                return originalDocument;
            }
        }
        return rerankedDocument;
    }

    private List<String> buildDocumentLookupKeys(Document document) {
        if (document == null) {
            return List.of("null");
        }
        List<String> lookupKeys = new java.util.ArrayList<>();
        String documentId = document.getId();
        String textHash = document.getText() == null ? "" : Integer.toHexString(document.getText().hashCode());
        StringBuilder builder = new StringBuilder();
        if (documentId != null) {
            builder.append(documentId);
        }
        builder.append('|');
        builder.append(textHash);
        lookupKeys.add(builder.toString());

        if (!textHash.isBlank()) {
            lookupKeys.add("|" + textHash);
        }
        if (documentId != null && !documentId.isBlank()) {
            lookupKeys.add(documentId + "|");
            lookupKeys.add(documentId);
        }
        return lookupKeys;
    }

    private List<Document> trim(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .limit(ragHybridRetrievalProperties.rerankTopN())
                .toList();
    }

    private void recordUsage(RerankResponse response, long latencyMs, boolean success, String errorMessage, Long workflowId) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        modelUsageLogService.save(new ModelUsageRecord(
                workflowId,
                null,
                null,
                null,
                null,
                "rag-rerank",
                aiModelProperties.rerankModel(),
                "dashscope",
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                latencyMs,
                success,
                errorMessage
        ));
    }
}
