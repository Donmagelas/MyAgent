package com.example.agentplatform.rag.service;

import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.RagHallucinationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RAG 回答后 judge 测试。
 * 先验证 judge 关闭时的安全回退行为，避免无谓依赖模型调用。
 */
class RagAnswerJudgeServiceTest {

    @Test
    void shouldSkipJudgeWhenJudgeDisabled() {
        RagEvidenceGuardService evidenceGuardService = new RagEvidenceGuardService(new RagHallucinationProperties(
                true,
                "当前检索到的证据不足以确认该问题，请提供更多上下文或更明确的资料。",
                new RagHallucinationProperties.Evidence(true, 1, 0.18d, 0.2d, 3),
                new RagHallucinationProperties.Judge(false, 0.0d, 256, 4, 420)
        ));
        RagAnswerJudgeService judgeService = new RagAnswerJudgeService(
                mock(ChatModel.class),
                new AiModelProperties("qwen3.5-flash", 0.2d, "qwen3-vl-embedding", "qwen3-vl-rerank"),
                new RagHallucinationProperties(
                        true,
                        "当前检索到的证据不足以确认该问题，请提供更多上下文或更明确的资料。",
                        new RagHallucinationProperties.Evidence(true, 1, 0.18d, 0.2d, 3),
                        new RagHallucinationProperties.Judge(false, 0.0d, 256, 4, 420)
                ),
                new SpringAiChatResponseMapper(),
                evidenceGuardService
        );

        RagAnswerJudgeService.StructuredJudgeResult result = judgeService.judge(
                "三角洲行动有哪些模式",
                "它包含多种模式。",
                List.of()
        );

        assertThat(result.body()).isNotNull();
        assertThat(result.body().grounded()).isTrue();
        assertThat(result.body().downgradeToInsufficient()).isFalse();
        assertThat(result.response()).isNull();
    }
}
