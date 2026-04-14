package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagHallucinationProperties;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 证据充分性守卫测试。
 * 验证空证据和有效证据两种基础场景。
 */
class RagEvidenceGuardServiceTest {

    @Test
    void shouldRejectWhenNoEvidenceFound() {
        RagEvidenceGuardService service = new RagEvidenceGuardService(defaultProperties());

        RagEvidenceAssessment assessment = service.assess("三角洲行动有哪些模式", List.of());

        assertThat(assessment.sufficient()).isFalse();
        assertThat(assessment.reason()).contains("未检索到任何相关证据");
    }

    @Test
    void shouldAcceptWhenEvidenceHasEnoughSignal() {
        RagEvidenceGuardService service = new RagEvidenceGuardService(defaultProperties());
        RetrievedChunk chunk = new RetrievedChunk(
                1L,
                10L,
                "三角洲行动_RAG测试文档.md",
                0,
                "三角洲行动包含战术射击与撤离模式、多角色协同作战、PVE与PVP混合体验。",
                Map.of(),
                0.61d,
                "vector+keyword+rerank"
        );

        RagEvidenceAssessment assessment = service.assess("三角洲行动有哪些模式", List.of(chunk));

        assertThat(assessment.sufficient()).isTrue();
        assertThat(assessment.sourceCount()).isEqualTo(1);
        assertThat(assessment.topScore()).isGreaterThan(0.18d);
    }

    private RagHallucinationProperties defaultProperties() {
        return new RagHallucinationProperties(
                true,
                "当前检索到的证据不足以确认该问题，请提供更多上下文或更明确的资料。",
                new RagHallucinationProperties.Evidence(true, 1, 0.18d, 0.2d, 3),
                new RagHallucinationProperties.Judge(true, 0.0d, 256, 4, 420)
        );
    }
}
