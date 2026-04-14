package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryExtractionTriggerType;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryExtractionTriggerPolicy 测试。
 * 用于锁定自动长期记忆提炼的周期触发和重要内容触发行为。
 */
class MemoryExtractionTriggerPolicyTest {

    @Test
    void shouldTriggerWhenAssistantTurnMatchesPeriodicInterval() {
        MemoryExtractionTriggerPolicy policy = new MemoryExtractionTriggerPolicy(buildMemoryProperties());

        MemoryExtractionTriggerPolicy.Decision decision = policy.decide(3, List.of(
                buildMessage("user", "只是普通对话")
        ));

        assertThat(decision.triggered()).isTrue();
        assertThat(decision.triggerType()).isEqualTo(MemoryExtractionTriggerType.PERIODIC);
    }

    @Test
    void shouldTriggerWhenRecentMessagesContainImportantKeyword() {
        MemoryExtractionTriggerPolicy policy = new MemoryExtractionTriggerPolicy(buildMemoryProperties());

        MemoryExtractionTriggerPolicy.Decision decision = policy.decide(1, List.of(
                buildMessage("user", "这次的设计决定已经确定了"),
                buildMessage("assistant", "我记录一下这个决定")
        ));

        assertThat(decision.triggered()).isTrue();
        assertThat(decision.triggerType()).isEqualTo(MemoryExtractionTriggerType.IMPORTANT_CONTENT);
    }

    @Test
    void shouldNotTriggerWhenNeitherPeriodicNorImportantContentMatches() {
        MemoryExtractionTriggerPolicy policy = new MemoryExtractionTriggerPolicy(buildMemoryProperties());

        MemoryExtractionTriggerPolicy.Decision decision = policy.decide(1, List.of(
                buildMessage("user", "你好"),
                buildMessage("assistant", "你好，很高兴见到你")
        ));

        assertThat(decision.triggered()).isFalse();
        assertThat(decision.triggerType()).isNull();
    }

    private MemoryProperties buildMemoryProperties() {
        return new MemoryProperties(
                8,
                10,
                5,
                8,
                List.of("USER_PREFERENCE", "PROJECT_STATUS"),
                new MemoryProperties.Extraction(
                        true,
                        3,
                        8,
                        true,
                        0.0,
                        768,
                        4,
                        List.of("决定", "prefer")
                )
        );
    }

    private RecentConversationMessage buildMessage(String role, String content) {
        return new RecentConversationMessage(
                1L,
                10L,
                100L,
                role,
                content,
                "TEXT",
                "qwen3.5-flash",
                OffsetDateTime.now()
        );
    }
}
