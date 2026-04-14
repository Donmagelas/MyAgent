package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryExtractionCandidate;
import com.example.agentplatform.memory.domain.MemoryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryExtractionCandidatePostProcessor 测试。
 * 用于验证身份、位置等稳定事实会被从 USER_PREFERENCE 纠偏为 STABLE_FACT。
 */
class MemoryExtractionCandidatePostProcessorTest {

    private final MemoryExtractionCandidatePostProcessor postProcessor =
            new MemoryExtractionCandidatePostProcessor();

    @Test
    void shouldRemapUserIdentityFactsToStableFact() {
        MemoryExtractionCandidate normalized = postProcessor.normalize(new MemoryExtractionCandidate(
                MemoryType.USER_PREFERENCE,
                "user_name",
                "User name is Periodic Tester.",
                9,
                "User identity: Periodic Tester"
        ));

        assertThat(normalized.memoryType()).isEqualTo(MemoryType.STABLE_FACT);
        assertThat(normalized.subject()).isEqualTo("user_name");
        assertThat(normalized.content()).isEqualTo("User name is Periodic Tester.");
    }

    @Test
    void shouldKeepRealPreferenceAsUserPreference() {
        MemoryExtractionCandidate normalized = postProcessor.normalize(new MemoryExtractionCandidate(
                MemoryType.USER_PREFERENCE,
                "response_style_preference",
                "The user prefers concise answers.",
                9,
                "User prefers concise answers"
        ));

        assertThat(normalized.memoryType()).isEqualTo(MemoryType.USER_PREFERENCE);
    }
}
