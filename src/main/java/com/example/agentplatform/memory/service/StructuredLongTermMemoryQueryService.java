package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.config.MemoryStructuredQueryProperties;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长期记忆结构化查询服务。
 * 负责把 Spring AI 结构化解析结果与规则解析结果合并，产出最终的长期记忆查询条件和 metadata 过滤器。
 */
@Service
public class StructuredLongTermMemoryQueryService {

    private static final Logger log = LoggerFactory.getLogger(StructuredLongTermMemoryQueryService.class);

    private final MemoryProperties memoryProperties;
    private final MemoryStructuredQueryProperties memoryStructuredQueryProperties;
    private final SpringAiMemoryQueryIntentParser springAiMemoryQueryIntentParser;
    private final RuleBasedMemoryQueryIntentParser ruleBasedMemoryQueryIntentParser;

    public StructuredLongTermMemoryQueryService(
            MemoryProperties memoryProperties,
            MemoryStructuredQueryProperties memoryStructuredQueryProperties,
            SpringAiMemoryQueryIntentParser springAiMemoryQueryIntentParser,
            RuleBasedMemoryQueryIntentParser ruleBasedMemoryQueryIntentParser
    ) {
        this.memoryProperties = memoryProperties;
        this.memoryStructuredQueryProperties = memoryStructuredQueryProperties;
        this.springAiMemoryQueryIntentParser = springAiMemoryQueryIntentParser;
        this.ruleBasedMemoryQueryIntentParser = ruleBasedMemoryQueryIntentParser;
    }

    /**
     * 根据用户问题构造长期记忆查询请求。
     */
    public LongTermMemoryQueryRequest build(Long userId, String question, Integer limit) {
        MemoryQueryIntent intent = parseIntent(question);
        return new LongTermMemoryQueryRequest(
                userId,
                intent.memoryTypes(),
                intent.subject(),
                intent.minImportance(),
                true,
                limit == null ? memoryProperties.stableFactLimit() : limit,
                intent.metadataFilter()
        );
    }

    /**
     * 解析自然语言问题的长期记忆查询意图。
     * 当前会同时支持 memory type / subject 和 metadata 过滤条件。
     */
    public MemoryQueryIntent parseIntent(String question) {
        MemoryQueryIntent ruleIntent = ruleBasedMemoryQueryIntentParser.parse(question);
        if (!memoryStructuredQueryProperties.enabled()) {
            return ruleIntent;
        }

        try {
            MemoryQueryIntent structuredIntent = springAiMemoryQueryIntentParser.parse(question);
            if (structuredIntent != null && structuredIntent.hasMeaningfulConstraint()) {
                return mergeIntents(structuredIntent.normalize(enabledMemoryTypes()), ruleIntent);
            }
        }
        catch (Exception exception) {
            log.warn("结构化记忆查询解析失败，回退到规则解析：{}", exception.getMessage());
        }
        return ruleIntent;
    }

    /**
     * 合并结构化解析结果与规则解析结果。
     * 当问题明显是 metadata 过滤型 query 时，优先保留规则解析出的 metadata 条件，避免 LLM 过度改写主题或类型。
     */
    private MemoryQueryIntent mergeIntents(MemoryQueryIntent structuredIntent, MemoryQueryIntent ruleIntent) {
        if (ruleIntent.metadataFilter() != null && ruleIntent.metadataFilter().hasConstraint()) {
            return new MemoryQueryIntent(
                    ruleIntent.memoryTypes(),
                    ruleIntent.subject(),
                    structuredIntent.minImportance() != null ? structuredIntent.minImportance() : ruleIntent.minImportance(),
                    structuredIntent.metadataFilter() != null && structuredIntent.metadataFilter().hasConstraint()
                            ? structuredIntent.metadataFilter()
                            : ruleIntent.metadataFilter()
            ).normalize(enabledMemoryTypes());
        }

        return new MemoryQueryIntent(
                structuredIntent.memoryTypes() != null && !structuredIntent.memoryTypes().isEmpty()
                        ? structuredIntent.memoryTypes()
                        : ruleIntent.memoryTypes(),
                structuredIntent.subject() != null && !structuredIntent.subject().isBlank()
                        ? structuredIntent.subject()
                        : ruleIntent.subject(),
                structuredIntent.minImportance() != null
                        ? structuredIntent.minImportance()
                        : ruleIntent.minImportance(),
                structuredIntent.metadataFilter() != null && structuredIntent.metadataFilter().hasConstraint()
                        ? structuredIntent.metadataFilter()
                        : ruleIntent.metadataFilter()
        ).normalize(enabledMemoryTypes());
    }

    private List<com.example.agentplatform.memory.domain.MemoryType> enabledMemoryTypes() {
        return memoryProperties.enabledMemoryTypes().stream()
                .map(com.example.agentplatform.memory.domain.MemoryType::valueOf)
                .toList();
    }
}
