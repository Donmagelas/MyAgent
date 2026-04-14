package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryExtractionTriggerType;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryExtractionPromptService 提示词测试。
 * 用于验证自动记忆提炼 prompt 不会再次出现乱码或规则退化。
 */
class MemoryExtractionPromptServiceTest {

    private static final String CORRUPTED_PLACEHOLDER = "\u003f\u003f\u003f\u003f";

    private final MemoryExtractionPromptService promptService = new MemoryExtractionPromptService();

    @Test
    void shouldBuildStableSystemPromptWithTypeMappingRules() {
        String prompt = promptService.buildSystemPrompt(MemoryExtractionTriggerType.PERIODIC);

        assertThat(prompt)
                .contains("You are the long-term memory extraction module")
                .contains("Type mapping rules:")
                .contains("A person's name, identity, nickname, role, or location is NOT a USER_PREFERENCE")
                .contains("\"The user's name is Alice\" is STABLE_FACT")
                .contains("\"The user prefers concise answers\" is USER_PREFERENCE")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }

    @Test
    void shouldBuildStableUserPrompt() {
        String prompt = promptService.buildUserPrompt(List.of(
                new RecentConversationMessage(
                        1L,
                        2L,
                        3L,
                        "user",
                        "I prefer concise answers.",
                        "TEXT",
                        null,
                        OffsetDateTime.now()
                ),
                new RecentConversationMessage(
                        2L,
                        2L,
                        3L,
                        "assistant",
                        "Understood. I will keep my responses concise.",
                        "TEXT",
                        "qwen3.5-flash",
                        OffsetDateTime.now()
                )
        ));

        assertThat(prompt)
                .contains("[user] I prefer concise answers.")
                .contains("[assistant] Understood. I will keep my responses concise.")
                .contains("Analyze this conversation window")
                .doesNotContain(CORRUPTED_PLACEHOLDER);
    }
}
