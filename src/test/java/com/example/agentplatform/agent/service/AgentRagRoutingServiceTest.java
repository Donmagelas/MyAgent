package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.RagIntentClassification;
import com.example.agentplatform.agent.domain.RagIntentDecision;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.config.AgentRagRoutingProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.service.RagProbeRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 路由编排服务测试。
 * 覆盖 AI 分类、检索探测和启发式兜底的组合决策。
 */
@ExtendWith(MockitoExtension.class)
class AgentRagRoutingServiceTest {

    @Mock
    private AgentRagRoutingHeuristicService heuristicService;
    @Mock
    private AgentRagIntentClassifierService classifierService;
    @Mock
    private RagProbeRetrievalService ragProbeRetrievalService;

    private AgentRagRoutingService service;
    private MemoryContext memoryContext;

    @BeforeEach
    void setUp() {
        AgentRagRoutingProperties properties = new AgentRagRoutingProperties(
                true,
                4,
                2,
                List.of("如何", "配置", "参数"),
                List.of("项目", "知识库"),
                List.of("写一首诗"),
                true,
                0.0d,
                256,
                true,
                1,
                0.18d,
                0.72d
        );
        service = new AgentRagRoutingService(properties, heuristicService, classifierService, ragProbeRetrievalService);
        memoryContext = new MemoryContext(List.of(), List.of(), List.of(), "");
    }

    @Test
    void shouldForceRagWhenUserExplicitlyPrefersKnowledgeRetrieval() {
        AgentChatRequest request = new AgentChatRequest(
                "session",
                "这份文档讲了什么",
                AgentReasoningMode.LOOP,
                3,
                true,
                "guide.md"
        );

        AgentRagRoutingService.RagRoutingDecision decision = service.decide(request, memoryContext, 10L);

        assertThat(decision.forceRag()).isTrue();
        assertThat(decision.routeSource()).isEqualTo("explicit");
        assertThat(decision.retrievalQuery()).contains("这份文档讲了什么", "guide.md");
        verify(classifierService, never()).classify(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.anyBoolean(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void shouldForceRagWhenClassifierReturnsMustRagWithEnoughConfidence() {
        AgentChatRequest request = new AgentChatRequest("session", "项目 rerank 模型如何配置", AgentReasoningMode.LOOP, 3);
        AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision =
                AgentRagRoutingHeuristicService.RagRoutingDecision.skip("信号不足");
        when(heuristicService.decide(request.message())).thenReturn(heuristicDecision);
        when(classifierService.classify(request.message(), memoryContext, false, null, heuristicDecision))
                .thenReturn(classifierResult(new RagIntentClassification(
                        RagIntentDecision.MUST_RAG,
                        0.91d,
                        "询问项目配置，需要知识库",
                        "rerank 模型配置",
                        false
                )));

        AgentRagRoutingService.RagRoutingDecision decision = service.decide(request, memoryContext, 10L);

        assertThat(decision.forceRag()).isTrue();
        assertThat(decision.routeSource()).isEqualTo("classifier");
        assertThat(decision.retrievalQuery()).isEqualTo("rerank 模型配置");
        verify(ragProbeRetrievalService, never()).probe(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void shouldUseRetrievalProbeWhenClassifierReturnsMaybeRag() {
        AgentChatRequest request = new AgentChatRequest("session", "哈基米在资料里是什么意思", AgentReasoningMode.LOOP, 3);
        AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision =
                AgentRagRoutingHeuristicService.RagRoutingDecision.skip("信号不足");
        when(heuristicService.decide(request.message())).thenReturn(heuristicDecision);
        when(classifierService.classify(request.message(), memoryContext, false, null, heuristicDecision))
                .thenReturn(classifierResult(new RagIntentClassification(
                        RagIntentDecision.MAYBE_RAG,
                        0.61d,
                        "可能是资料内定义",
                        "哈基米 含义",
                        false
                )));
        when(ragProbeRetrievalService.probe("哈基米 含义", 10L))
                .thenReturn(new RagProbeRetrievalService.ProbeResult(List.of(), 1, 0.42d, 1, 0));

        AgentRagRoutingService.RagRoutingDecision decision = service.decide(request, memoryContext, 10L);

        assertThat(decision.forceRag()).isTrue();
        assertThat(decision.routeSource()).isEqualTo("probe");
        assertThat(decision.probeExecuted()).isTrue();
        assertThat(decision.probeHitCount()).isEqualTo(1);
    }

    @Test
    void shouldRespectHighConfidenceNoRagClassifierBeforeHeuristicFallback() {
        AgentChatRequest request = new AgentChatRequest("session", "帮我写一首诗", AgentReasoningMode.LOOP, 3);
        AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision =
                AgentRagRoutingHeuristicService.RagRoutingDecision.force("误命中启发式");
        when(heuristicService.decide(request.message())).thenReturn(heuristicDecision);
        when(classifierService.classify(request.message(), memoryContext, false, null, heuristicDecision))
                .thenReturn(classifierResult(new RagIntentClassification(
                        RagIntentDecision.NO_RAG,
                        0.95d,
                        "创作任务不需要知识库",
                        request.message(),
                        false
                )));

        AgentRagRoutingService.RagRoutingDecision decision = service.decide(request, memoryContext, 10L);

        assertThat(decision.forceRag()).isFalse();
        assertThat(decision.terminal()).isTrue();
        assertThat(decision.routeSource()).isEqualTo("classifier");
    }

    private AgentRagIntentClassifierService.StructuredResult classifierResult(RagIntentClassification classification) {
        return new AgentRagIntentClassifierService.StructuredResult(classification, null);
    }
}
