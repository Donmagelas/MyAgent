package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHybridRetrievalProperties;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索结果融合服务。
 * 负责把向量检索和关键词检索结果去重、打分并生成进入 rerank 前的候选集。
 */
@Component
public class HybridDocumentFusionService {

    private static final Logger log = LoggerFactory.getLogger(HybridDocumentFusionService.class);
    private final RagHybridRetrievalProperties ragHybridRetrievalProperties;

    public HybridDocumentFusionService(RagHybridRetrievalProperties ragHybridRetrievalProperties) {
        this.ragHybridRetrievalProperties = ragHybridRetrievalProperties;
    }

    /**
     * 对两路候选结果执行去重和加权融合。
     */
    public List<Document> fuse(List<Document> vectorDocuments, List<Document> keywordDocuments) {
        Map<String, FusedCandidate> fusedCandidates = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();

        mergeDocuments(fusedCandidates, aliases, vectorDocuments, "vector", ragHybridRetrievalProperties.vectorWeight());
        mergeDocuments(fusedCandidates, aliases, keywordDocuments, "keyword", ragHybridRetrievalProperties.keywordWeight());

        return fusedCandidates.values().stream()
                .sorted((left, right) -> Double.compare(right.fusionScore(), left.fusionScore()))
                .limit(ragHybridRetrievalProperties.fusionCandidateLimit())
                .map(FusedCandidate::toDocument)
                .toList();
    }

    private void mergeDocuments(
            Map<String, FusedCandidate> fusedCandidates,
            Map<String, String> aliases,
            List<Document> documents,
            String retrievalType,
            double weight
    ) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            List<String> identities = buildIdentities(document);
            String primaryKey = resolvePrimaryKey(aliases, identities);
            double baseScore = document.getScore() == null ? 0.0d : document.getScore();
            double rankScore = 1.0d / (index + 1);
            double weightedScore = (baseScore * weight) + (rankScore * weight);

            if (primaryKey == null) {
                primaryKey = identities.get(0);
                fusedCandidates.put(primaryKey, FusedCandidate.from(document, retrievalType, weightedScore, baseScore));
            }
            else {
                fusedCandidates.computeIfPresent(primaryKey,
                        (key, existing) -> existing.merge(document, retrievalType, weightedScore, baseScore));
            }
            registerAliases(aliases, primaryKey, identities);
        }
        log.debug("融合阶段类型='{}' 输入数={} 当前候选数={}", retrievalType, documents.size(), fusedCandidates.size());
    }

    private String resolvePrimaryKey(Map<String, String> aliases, List<String> identities) {
        for (String identity : identities) {
            String primaryKey = aliases.get(identity);
            if (primaryKey != null) {
                return primaryKey;
            }
        }
        return null;
    }

    private void registerAliases(Map<String, String> aliases, String primaryKey, List<String> identities) {
        for (String identity : identities) {
            aliases.putIfAbsent(identity, primaryKey);
        }
    }

    private List<String> buildIdentities(Document document) {
        List<String> identities = new ArrayList<>();
        Map<String, Object> metadata = document.getMetadata();
        if (metadata != null) {
            Object chunkId = metadata.get(DocumentMetadataKeys.CHUNK_ID);
            if (chunkId != null) {
                identities.add("chunk:" + chunkId);
            }
            Object documentId = metadata.get(DocumentMetadataKeys.DOCUMENT_ID);
            Object chunkIndex = metadata.get(DocumentMetadataKeys.CHUNK_INDEX);
            if (documentId != null && chunkIndex != null) {
                identities.add("document:" + documentId + ":" + chunkIndex);
            }
            Object documentTitle = metadata.get(DocumentMetadataKeys.DOCUMENT_TITLE);
            if (documentTitle != null && chunkIndex != null) {
                identities.add("title:" + documentTitle + ":" + chunkIndex);
            }
            if (documentTitle != null) {
                identities.add("title-only:" + documentTitle);
            }
            Object sourceUri = metadata.get(DocumentMetadataKeys.SOURCE_URI);
            if (sourceUri != null && chunkIndex != null) {
                identities.add("source:" + sourceUri + ":" + chunkIndex);
            }
            if (sourceUri != null) {
                identities.add("source-only:" + sourceUri);
            }
        }
        if (document.getText() != null && !document.getText().isBlank()) {
            identities.add("content:" + Integer.toHexString(document.getText().hashCode()));
        }
        identities.add("document-id:" + document.getId());
        return identities;
    }

    /**
     * 融合中的候选文档。
     */
    private record FusedCandidate(
            Document document,
            Set<String> retrievalTypes,
            double fusionScore,
            Double vectorScore,
            Double keywordScore
    ) {

        static FusedCandidate from(Document document, String retrievalType, double fusionScore, double baseScore) {
            LinkedHashSet<String> retrievalTypes = new LinkedHashSet<>();
            retrievalTypes.add(retrievalType);
            return new FusedCandidate(
                    document,
                    retrievalTypes,
                    fusionScore,
                    "vector".equals(retrievalType) ? baseScore : null,
                    "keyword".equals(retrievalType) ? baseScore : null
            );
        }

        FusedCandidate merge(Document incomingDocument, String retrievalType, double scoreIncrement, double baseScore) {
            LinkedHashSet<String> mergedTypes = new LinkedHashSet<>(retrievalTypes);
            mergedTypes.add(retrievalType);
            Document preferredDocument = pickPreferredDocument(document, incomingDocument);
            return new FusedCandidate(
                    preferredDocument,
                    mergedTypes,
                    fusionScore + scoreIncrement,
                    mergeScore(vectorScore, retrievalType, "vector", baseScore),
                    mergeScore(keywordScore, retrievalType, "keyword", baseScore)
            );
        }

        Document toDocument() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            metadata.put(DocumentMetadataKeys.RETRIEVAL_TYPE, RetrievalTypeNormalizer.normalize(retrievalTypes));
            if (vectorScore != null) {
                metadata.put(DocumentMetadataKeys.VECTOR_SCORE, vectorScore);
            }
            if (keywordScore != null) {
                metadata.put(DocumentMetadataKeys.KEYWORD_SCORE, keywordScore);
            }
            metadata.put(DocumentMetadataKeys.FUSION_SCORE, fusionScore);
            return document.mutate()
                    .metadata(metadata)
                    .score(fusionScore)
                    .build();
        }

        private static Document pickPreferredDocument(Document current, Document incoming) {
            double currentScore = current.getScore() == null ? 0.0d : current.getScore();
            double incomingScore = incoming.getScore() == null ? 0.0d : incoming.getScore();
            if (incomingScore > currentScore) {
                return incoming;
            }
            return current;
        }

        private static Double mergeScore(Double existing, String actualType, String expectedType, double incomingScore) {
            if (!expectedType.equals(actualType)) {
                return existing;
            }
            if (existing == null) {
                return incomingScore;
            }
            return Math.max(existing, incomingScore);
        }
    }
}
