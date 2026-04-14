package com.example.agentplatform.memory.domain;

import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemoryQueryIntent 测试。
 * 用于验证 metadataFilter 会参与有效约束判断与 normalize。
 */
class MemoryQueryIntentTest {

    @Test
    void shouldTreatMetadataFilterAsMeaningfulConstraint() {
        MemoryQueryIntent intent = new MemoryQueryIntent(
                List.of(),
                null,
                null,
                new MemoryMetadataFilter(Boolean.TRUE, "periodic", 12L)
        );

        assertThat(intent.hasMeaningfulConstraint()).isTrue();
        assertThat(intent.normalize(List.of(MemoryType.STABLE_FACT)).metadataFilter().triggerType())
                .isEqualTo("PERIODIC");
    }
}
