package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import com.example.agentplatform.memory.domain.MemoryType;
import com.example.agentplatform.memory.dto.MemoryMetadataFilter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于规则的记忆查询意图解析器。
 * 用于在不额外调用模型的情况下，从自然语言问题里提炼 memory type、subject 和 metadata 过滤条件。
 */
@Service
public class RuleBasedMemoryQueryIntentParser implements MemoryQueryIntentParser {

    /**
     * 匹配引号包裹的主题词，例如 “Nebula”。
     */
    private static final Pattern QUOTED_SUBJECT_PATTERN =
            Pattern.compile("[\"'\u201c\u201d\u2018\u2019]([^\"'\u201c\u201d\u2018\u2019]{2,40})[\"'\u201c\u201d\u2018\u2019]");

    /**
     * 匹配“关于 / 有关 / 针对 xxx”的主题表达。
     */
    private static final Pattern ABOUT_SUBJECT_PATTERN =
            Pattern.compile("(?:\u5173\u4e8e|\u6709\u5173|\u9488\u5bf9|about)\\s*([\\p{L}\\p{N}_\\-\\u4e00-\\u9fa5]{2,40})", Pattern.CASE_INSENSITIVE);

    /**
     * 从 query 中提取 assistantMessageId。
     */
    private static final Pattern ASSISTANT_MESSAGE_ID_PATTERN =
            Pattern.compile("(?:assistantmessageid|assistant_message_id|assistant message id|assistant-message-id|\u6d88\u606fid|\u6d88\u606f\u7f16\u53f7)\\s*[:=?#]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final MemoryProperties memoryProperties;

    public RuleBasedMemoryQueryIntentParser(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    @Override
    public MemoryQueryIntent parse(String question) {
        return new MemoryQueryIntent(
                inferMemoryTypes(question),
                extractSubject(question),
                inferMinImportance(question),
                inferMetadataFilter(question)
        ).normalize(enabledMemoryTypes());
    }

    private List<MemoryType> inferMemoryTypes(String question) {
        String normalized = normalize(question);
        Set<MemoryType> types = new LinkedHashSet<>();

        if (containsAny(normalized,
                "\u504f\u597d", "\u559c\u6b22", "\u4e60\u60ef", "preference", "prefer", "like", "habit")) {
            types.add(MemoryType.USER_PREFERENCE);
        }
        if (containsAny(normalized,
                "\u9879\u76ee", "\u8fdb\u5ea6", "\u72b6\u6001", "\u91cc\u7a0b\u7891", "project", "progress", "status", "milestone")) {
            types.add(MemoryType.PROJECT_STATUS);
        }
        if (containsAny(normalized,
                "\u8bbe\u8ba1", "\u67b6\u6784", "\u51b3\u7b56", "\u53d6\u820d", "decision", "design", "architecture", "tradeoff")) {
            types.add(MemoryType.DESIGN_DECISION);
        }
        if (containsAny(normalized,
                "\u4efb\u52a1", "\u7ed3\u8bba", "\u7ed3\u679c", "\u5b8c\u6210", "task", "result", "conclusion", "outcome", "done")) {
            types.add(MemoryType.TASK_CONCLUSION);
        }
        if (containsAny(normalized,
                "\u7a33\u5b9a\u4e8b\u5b9e", "\u8eab\u4efd", "\u59d3\u540d", "\u4f4d\u7f6e", "\u4e8b\u5b9e", "name", "who", "fact")) {
            types.add(MemoryType.STABLE_FACT);
        }

        if (types.isEmpty()) {
            return enabledMemoryTypes();
        }
        return List.copyOf(types);
    }

    private String extractSubject(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        Matcher quotedMatcher = QUOTED_SUBJECT_PATTERN.matcher(question);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Matcher aboutMatcher = ABOUT_SUBJECT_PATTERN.matcher(question);
        if (aboutMatcher.find()) {
            return aboutMatcher.group(1).trim();
        }

        return null;
    }

    private Integer inferMinImportance(String question) {
        String normalized = normalize(question);
        if (containsAny(normalized, "\u6700\u9ad8", "\u5173\u952e", "\u6838\u5fc3", "highest", "most important", "critical")) {
            return 8;
        }
        return null;
    }

    private MemoryMetadataFilter inferMetadataFilter(String question) {
        String normalized = normalize(question);
        Boolean autoExtracted = containsAny(normalized,
                "\u81ea\u52a8\u63d0\u70bc", "\u81ea\u52a8\u8bb0\u5fc6", "\u81ea\u52a8\u63d0\u53d6", "auto extracted", "auto-extraction", "auto extraction")
                ? Boolean.TRUE
                : null;

        String triggerType = null;
        if (containsAny(normalized, "\u5468\u671f", "\u5b9a\u671f", "\u5468\u671f\u89e6\u53d1", "periodic")) {
            triggerType = "PERIODIC";
        }
        else if (containsAny(normalized,
                "\u91cd\u8981\u5185\u5bb9", "\u91cd\u8981\u5bf9\u8bdd", "\u91cd\u8981\u89e6\u53d1", "important content", "important conversation")) {
            triggerType = "IMPORTANT_CONTENT";
        }

        Long assistantMessageId = null;
        if (question != null && !question.isBlank()) {
            Matcher matcher = ASSISTANT_MESSAGE_ID_PATTERN.matcher(question);
            if (matcher.find()) {
                assistantMessageId = Long.parseLong(matcher.group(1));
            }
        }

        MemoryMetadataFilter filter = new MemoryMetadataFilter(autoExtracted, triggerType, assistantMessageId);
        return filter.hasConstraint() ? filter : null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<MemoryType> enabledMemoryTypes() {
        return memoryProperties.enabledMemoryTypes().stream()
                .map(MemoryType::valueOf)
                .toList();
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
    }
}
