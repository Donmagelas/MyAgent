package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.config.MemoryStructuredQueryProperties;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import com.example.agentplatform.memory.domain.MemoryType;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StructuredLongTermMemoryQueryService 测试。
 * 验证 metadata 导向 query 会优先保留规则解析出的 metadata 条件。
 */
class StructuredLongTermMemoryQueryServiceTest {

    @Test
    void shouldPreferRuleIntentForMetadataFocusedQuery() {
        SpringAiMemoryQueryIntentParser structuredParser = mock(SpringAiMemoryQueryIntentParser.class);
        RuleBasedMemoryQueryIntentParser ruleParser = mock(RuleBasedMemoryQueryIntentParser.class);
        StructuredLongTermMemoryQueryService service = new StructuredLongTermMemoryQueryService(
                new MemoryProperties(
                        8,
                        10,
                        5,
                        8,
                        List.of("USER_PREFERENCE", "PROJECT_STATUS", "DESIGN_DECISION", "TASK_CONCLUSION", "STABLE_FACT"),
                        null
                ),
                new MemoryStructuredQueryProperties(true, 0.0d, 256),
                structuredParser,
                ruleParser
        );

        when(ruleParser.parse("查询自动提炼的周期触发长期记忆"))
                .thenReturn(new MemoryQueryIntent(
                        List.of(MemoryType.STABLE_FACT),
                        null,
                        null,
                        new MemoryMetadataFilter(Boolean.TRUE, "PERIODIC", null)
                ));
        when(structuredParser.parse("查询自动提炼的周期触发长期记忆"))
                .thenReturn(new MemoryQueryIntent(
                        List.of(MemoryType.TASK_CONCLUSION),
                        "自动记忆",
                        null,
                        null
                ));

        MemoryQueryIntent merged = service.parseIntent("查询自动提炼的周期触发长期记忆");

        assertThat(merged.memoryTypes()).containsExactly(MemoryType.STABLE_FACT);
        assertThat(merged.subject()).isNull();
        assertThat(merged.metadataFilter()).isNotNull();
        assertThat(merged.metadataFilter().triggerType()).isEqualTo("PERIODIC");
        assertThat(merged.metadataFilter().autoExtracted()).isTrue();
    }
}
