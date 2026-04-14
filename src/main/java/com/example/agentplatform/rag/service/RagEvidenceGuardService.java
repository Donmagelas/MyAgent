package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHallucinationProperties;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 证据充分性守卫服务。
 * 在真正进入 grounded-answer 前先用启发式规则判断证据是否足够，避免空证据或弱证据直接进入回答阶段。
 */
@Service
public class RagEvidenceGuardService {

    private static final Pattern QUERY_TERM_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9]{2,}");

    private final RagHallucinationProperties ragHallucinationProperties;

    public RagEvidenceGuardService(RagHallucinationProperties ragHallucinationProperties) {
        this.ragHallucinationProperties = ragHallucinationProperties;
    }

    /**
     * 评估当前检索结果是否足以支撑 grounded-answer。
     */
    public RagEvidenceAssessment assess(String query, List<RetrievedChunk> chunks) {
        if (!ragHallucinationProperties.enabled()
                || ragHallucinationProperties.evidence() == null
                || !ragHallucinationProperties.evidence().enabled()) {
            return new RagEvidenceAssessment(true, "证据 gate 已关闭", chunks == null ? 0 : chunks.size(), 0.0d, 1.0d);
        }
        if (chunks == null || chunks.isEmpty()) {
            return new RagEvidenceAssessment(false, "未检索到任何相关证据", 0, 0.0d, 0.0d);
        }

        int inspectedChunks = Math.max(ragHallucinationProperties.evidence().inspectedChunks(), 1);
        List<RetrievedChunk> inspected = chunks.stream().limit(inspectedChunks).toList();
        double topScore = inspected.stream()
                .mapToDouble(RetrievedChunk::score)
                .max()
                .orElse(0.0d);
        double lexicalCoverage = calculateLexicalCoverage(query, inspected);

        boolean enoughSources = inspected.size() >= Math.max(ragHallucinationProperties.evidence().minSourceCount(), 1);
        boolean enoughSignal = topScore >= ragHallucinationProperties.evidence().minTopScore()
                || lexicalCoverage >= ragHallucinationProperties.evidence().minLexicalCoverage();
        if (enoughSources && enoughSignal) {
            return new RagEvidenceAssessment(true, "证据充分", inspected.size(), topScore, lexicalCoverage);
        }

        String reason = "证据不足：sourceCount=%d, topScore=%.4f, lexicalCoverage=%.2f"
                .formatted(inspected.size(), topScore, lexicalCoverage);
        return new RagEvidenceAssessment(false, reason, inspected.size(), topScore, lexicalCoverage);
    }

    /**
     * 返回统一的证据不足降级文案。
     */
    public String buildInsufficientAnswer() {
        String configured = ragHallucinationProperties.insufficientAnswer();
        if (configured == null || configured.isBlank()) {
            return "当前检索到的证据不足以确认该问题，请提供更多上下文或更明确的资料。";
        }
        return configured;
    }

    private double calculateLexicalCoverage(String query, List<RetrievedChunk> chunks) {
        Set<String> queryTerms = extractQueryTerms(query);
        if (queryTerms.isEmpty()) {
            return 1.0d;
        }
        String evidenceText = chunks.stream()
                .map(this::buildEvidenceText)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
        long matchedCount = queryTerms.stream()
                .filter(evidenceText::contains)
                .count();
        return (double) matchedCount / queryTerms.size();
    }

    private Set<String> extractQueryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = QUERY_TERM_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            terms.add(matcher.group());
        }
        return terms;
    }

    private String buildEvidenceText(RetrievedChunk chunk) {
        StringBuilder builder = new StringBuilder();
        if (chunk.documentTitle() != null) {
            builder.append(chunk.documentTitle()).append('\n');
        }
        if (chunk.chunkTitle() != null) {
            builder.append(chunk.chunkTitle()).append('\n');
        }
        if (chunk.sectionPath() != null) {
            builder.append(chunk.sectionPath()).append('\n');
        }
        if (chunk.jsonPath() != null) {
            builder.append(chunk.jsonPath()).append('\n');
        }
        if (chunk.content() != null) {
            builder.append(chunk.content());
        }
        return builder.toString();
    }
}
