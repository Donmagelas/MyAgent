package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.RagIntentClassification;
import com.example.agentplatform.agent.domain.RagIntentDecision;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.config.AgentRagRoutingProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.service.RagProbeRetrievalService;
import org.springframework.stereotype.Service;

/**
 * Agent 主链路的 RAG 路由编排服务。
 * 组合显式规则、AI 意图分类、启发式信号和检索探测，输出程序最终决策。
 */
@Service
public class AgentRagRoutingService {

    private final AgentRagRoutingProperties properties;
    private final AgentRagRoutingHeuristicService heuristicService;
    private final AgentRagIntentClassifierService classifierService;
    private final RagProbeRetrievalService ragProbeRetrievalService;

    public AgentRagRoutingService(
            AgentRagRoutingProperties properties,
            AgentRagRoutingHeuristicService heuristicService,
            AgentRagIntentClassifierService classifierService,
            RagProbeRetrievalService ragProbeRetrievalService
    ) {
        this.properties = properties;
        this.heuristicService = heuristicService;
        this.classifierService = classifierService;
        this.ragProbeRetrievalService = ragProbeRetrievalService;
    }

    /**
     * 输出是否应该把当前第一步计划改写为 RAG。
     */
    public RagRoutingDecision decide(
            AgentChatRequest request,
            MemoryContext memoryContext,
            Long workflowId
    ) {
        if (!properties.enabled()) {
            return RagRoutingDecision.skip("disabled", "RAG routing is disabled.", buildQuery(request), null);
        }

        String fallbackQuery = buildQuery(request);
        boolean explicitKnowledgePreference = Boolean.TRUE.equals(request.preferKnowledgeRetrieval());
        if (explicitKnowledgePreference) {
            return RagRoutingDecision.force(
                    "explicit",
                    "The current conversation explicitly prefers knowledge-base retrieval.",
                    fallbackQuery,
                    null
            );
        }

        AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision =
                heuristicService.decide(request.message());
        AgentRagIntentClassifierService.StructuredResult classifierResult = classifySafely(
                request,
                memoryContext,
                heuristicDecision
        );

        if (classifierResult != null && classifierResult.body() != null) {
            RagRoutingDecision classified = decideFromClassifier(classifierResult, fallbackQuery, workflowId);
            if (classified.forceRag()) {
                return classified;
            }
            if (classified.terminal()) {
                return classified;
            }
        }

        if (heuristicDecision.forceRag()) {
            return RagRoutingDecision.force(
                    "heuristic",
                    heuristicDecision.reason(),
                    fallbackQuery,
                    classifierResult
            );
        }

        return RagRoutingDecision.skip(
                "heuristic",
                heuristicDecision.reason(),
                fallbackQuery,
                classifierResult
        );
    }

    private AgentRagIntentClassifierService.StructuredResult classifySafely(
            AgentChatRequest request,
            MemoryContext memoryContext,
            AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision
    ) {
        if (!properties.classifierEnabled()) {
            return null;
        }
        try {
            return classifierService.classify(
                    request.message(),
                    memoryContext,
                    hasKnowledgeDocumentHint(request),
                    request.knowledgeDocumentHint(),
                    heuristicDecision
            );
        }
        catch (RuntimeException ignored) {
            return null;
        }
    }

    private RagRoutingDecision decideFromClassifier(
            AgentRagIntentClassifierService.StructuredResult classifierResult,
            String fallbackQuery,
            Long workflowId
    ) {
        RagIntentClassification classification = classifierResult.body();
        String retrievalQuery = classification.retrievalQuery() == null || classification.retrievalQuery().isBlank()
                ? fallbackQuery
                : classification.retrievalQuery();

        if (classification.decision() == RagIntentDecision.MUST_RAG
                && classification.confidence() >= properties.mustRagConfidenceThreshold()) {
            return RagRoutingDecision.force(
                    "classifier",
                    classification.reason(),
                    retrievalQuery,
                    classifierResult
            );
        }

        if (classification.decision() == RagIntentDecision.MAYBE_RAG) {
            return decideByProbe(classification, retrievalQuery, workflowId, classifierResult);
        }

        if (classification.decision() == RagIntentDecision.NO_RAG && classification.confidence() >= 0.85d) {
            return RagRoutingDecision.terminalSkip(
                    "classifier",
                    classification.reason(),
                    retrievalQuery,
                    classifierResult
            );
        }

        return RagRoutingDecision.skip(
                "classifier",
                classification.reason(),
                retrievalQuery,
                classifierResult
        );
    }

    private RagRoutingDecision decideByProbe(
            RagIntentClassification classification,
            String retrievalQuery,
            Long workflowId,
            AgentRagIntentClassifierService.StructuredResult classifierResult
    ) {
        if (!properties.maybeProbeEnabled()) {
            return RagRoutingDecision.skip(
                    "classifier",
                    classification.reason(),
                    retrievalQuery,
                    classifierResult
            );
        }
        RagProbeRetrievalService.ProbeResult probeResult;
        try {
            probeResult = ragProbeRetrievalService.probe(retrievalQuery, workflowId);
        }
        catch (RuntimeException exception) {
            return RagRoutingDecision.skipWithProbe(
                    "probe",
                    classification.reason() + "; probe failed: " + exception.getMessage(),
                    retrievalQuery,
                    classifierResult,
                    true,
                    0,
                    0.0d,
                    false
            );
        }
        int hitCount = probeResult == null ? 0 : probeResult.hitCount();
        double topScore = probeResult == null ? 0.0d : probeResult.topScore();
        String probeReason = "probe hits=" + hitCount
                + ", topScore=" + topScore
                + ", vectorHits=" + (probeResult == null ? 0 : probeResult.vectorHitCount())
                + ", keywordHits=" + (probeResult == null ? 0 : probeResult.keywordHitCount());
        if (hitCount >= properties.maybeProbeMinHits() && topScore >= properties.maybeProbeMinScore()) {
            return RagRoutingDecision.forceWithProbe(
                    "probe",
                    classification.reason() + "; " + probeReason,
                    retrievalQuery,
                    classifierResult,
                    true,
                    hitCount,
                    topScore
            );
        }
        return RagRoutingDecision.skipWithProbe(
                "probe",
                classification.reason() + "; " + probeReason,
                retrievalQuery,
                classifierResult,
                true,
                hitCount,
                topScore,
                true
        );
    }

    private String buildQuery(AgentChatRequest request) {
        if (request.knowledgeDocumentHint() == null || request.knowledgeDocumentHint().isBlank()) {
            return request.message();
        }
        return request.message() + " " + request.knowledgeDocumentHint();
    }

    private boolean hasKnowledgeDocumentHint(AgentChatRequest request) {
        return request.knowledgeDocumentHint() != null && !request.knowledgeDocumentHint().isBlank();
    }

    /**
     * 最终 RAG 路由决策，包含 AI 分类和检索探测的可视化元数据。
     */
    public record RagRoutingDecision(
            boolean forceRag,
            String routeSource,
            String reason,
            String retrievalQuery,
            AgentRagIntentClassifierService.StructuredResult classifierResult,
            boolean probeExecuted,
            int probeHitCount,
            double probeTopScore,
            boolean terminal
    ) {

        public static RagRoutingDecision force(
                String routeSource,
                String reason,
                String retrievalQuery,
                AgentRagIntentClassifierService.StructuredResult classifierResult
        ) {
            return new RagRoutingDecision(true, routeSource, reason, retrievalQuery, classifierResult, false, 0, 0.0d, false);
        }

        public static RagRoutingDecision forceWithProbe(
                String routeSource,
                String reason,
                String retrievalQuery,
                AgentRagIntentClassifierService.StructuredResult classifierResult,
                boolean probeExecuted,
                int probeHitCount,
                double probeTopScore
        ) {
            return new RagRoutingDecision(true, routeSource, reason, retrievalQuery, classifierResult, probeExecuted, probeHitCount, probeTopScore, false);
        }

        public static RagRoutingDecision skip(
                String routeSource,
                String reason,
                String retrievalQuery,
                AgentRagIntentClassifierService.StructuredResult classifierResult
        ) {
            return new RagRoutingDecision(false, routeSource, reason, retrievalQuery, classifierResult, false, 0, 0.0d, false);
        }

        public static RagRoutingDecision terminalSkip(
                String routeSource,
                String reason,
                String retrievalQuery,
                AgentRagIntentClassifierService.StructuredResult classifierResult
        ) {
            return new RagRoutingDecision(false, routeSource, reason, retrievalQuery, classifierResult, false, 0, 0.0d, true);
        }

        public static RagRoutingDecision skipWithProbe(
                String routeSource,
                String reason,
                String retrievalQuery,
                AgentRagIntentClassifierService.StructuredResult classifierResult,
                boolean probeExecuted,
                int probeHitCount,
                double probeTopScore,
                boolean terminal
        ) {
            return new RagRoutingDecision(false, routeSource, reason, retrievalQuery, classifierResult, probeExecuted, probeHitCount, probeTopScore, terminal);
        }
    }
}
