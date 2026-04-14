package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleBasedMemoryQueryIntentParser 测试。
 * 验证自然语言 query 能正确提炼 metadata 过滤条件。
 */
class RuleBasedMemoryQueryIntentParserTest {

    private final RuleBasedMemoryQueryIntentParser parser = new RuleBasedMemoryQueryIntentParser(
            new MemoryProperties(
                    8,
                    10,
                    5,
                    8,
                    List.of("USER_PREFERENCE", "PROJECT_STATUS", "DESIGN_DECISION", "TASK_CONCLUSION", "STABLE_FACT"),
                    null
            )
    );

    @Test
    void shouldInferPeriodicMetadataFilterFromQuery() {
        MemoryQueryIntent intent = parser.parse("查询自动提炼的周期触发长期记忆");

        assertThat(intent.metadataFilter()).isNotNull();
        assertThat(intent.metadataFilter().autoExtracted()).isTrue();
        assertThat(intent.metadataFilter().triggerType()).isEqualTo("PERIODIC");
    }

    @Test
    void shouldInferAssistantMessageIdFromQuery() {
        MemoryQueryIntent intent = parser.parse("帮我查 assistantMessageId=271 生成的自动记忆");

        assertThat(intent.metadataFilter()).isNotNull();
        assertThat(intent.metadataFilter().autoExtracted()).isTrue();
        assertThat(intent.metadataFilter().assistantMessageId()).isEqualTo(271L);
    }
}
