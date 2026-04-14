package com.example.agentplatform.agent.service;

import com.example.agentplatform.config.AgentRagRoutingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentRagRoutingHeuristicService 测试。
 * 验证知识型问题会提升 RAG 倾向，闲聊类请求不会被误判。
 */
class AgentRagRoutingHeuristicServiceTest {

    private final AgentRagRoutingHeuristicService service = new AgentRagRoutingHeuristicService(
            new AgentRagRoutingProperties(
                    true,
                    4,
                    2,
                    List.of("是什么", "如何", "怎么", "支持", "meaning", "what", "how"),
                    List.of("项目", "模型", "配置", "字段", "接口", "玩法", "武器", "rerank", "json"),
                    List.of("你好", "你是谁", "闲聊", "翻译", "润色")
            )
    );

    @Test
    void shouldForceRagForKnowledgeQuestion() {
        AgentRagRoutingHeuristicService.RagRoutingDecision decision =
                service.decide("项目里 project.retrieval.rerank 是什么，如何配置？");

        assertThat(decision.forceRag()).isTrue();
        assertThat(decision.reason()).contains("知识型问题启发式命中");
    }

    @Test
    void shouldSkipRagForSmallTalk() {
        AgentRagRoutingHeuristicService.RagRoutingDecision decision =
                service.decide("你好，你是谁？");

        assertThat(decision.forceRag()).isFalse();
    }

    @Test
    void shouldSkipRagWhenPositiveSignalsAreInsufficient() {
        AgentRagRoutingHeuristicService.RagRoutingDecision decision =
                service.decide("帮我查查哈基米是什么意思");

        assertThat(decision.forceRag()).isFalse();
    }
}
