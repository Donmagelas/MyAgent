package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryType;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 长期记忆结构化查询构建器。
 * 根据当前问题推导长期记忆的类型、主题和基础过滤条件。
 */
@Service
public class LongTermMemoryQueryBuilder {

    private static final Pattern QUOTED_SUBJECT_PATTERN = Pattern.compile("[\"“”'‘’]([^\"“”'‘’]{2,40})[\"“”'‘’]");
    private static final Pattern ABOUT_SUBJECT_PATTERN = Pattern.compile("(?:关于|有关|针对|about)\\s*([\\p{L}\\p{N}_\\-\\u4e00-\\u9fa5]{2,40})");

    private final MemoryProperties memoryProperties;

    public LongTermMemoryQueryBuilder(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /** 基于当前问题构建一次结构化长期记忆查询。 */
    public LongTermMemoryQueryRequest build(Long userId, String question, Integer limit) {
        List<MemoryType> inferredTypes = inferMemoryTypes(question);
        return new LongTermMemoryQueryRequest(
                userId,
                inferredTypes,
                extractSubject(question),
                null,
                true,
                limit == null ? memoryProperties.stableFactLimit() : limit,
                null
        );
    }

    private List<MemoryType> inferMemoryTypes(String question) {
        String normalized = normalize(question);
        Set<MemoryType> types = new LinkedHashSet<>();

        if (containsAny(normalized, "喜欢", "偏好", "倾向", "习惯", "爱好", "preference", "prefer", "like", "habit")) {
            types.add(MemoryType.USER_PREFERENCE);
        }
        if (containsAny(normalized, "项目", "进度", "状态", "里程碑", "project", "progress", "status", "milestone")) {
            types.add(MemoryType.PROJECT_STATUS);
        }
        if (containsAny(normalized, "决定", "决策", "选型", "设计", "方案", "取舍", "decision", "design", "architecture", "tradeoff")) {
            types.add(MemoryType.DESIGN_DECISION);
        }
        if (containsAny(normalized, "任务", "结果", "结论", "完成", "产出", "task", "result", "conclusion", "outcome", "done")) {
            types.add(MemoryType.TASK_CONCLUSION);
        }
        if (containsAny(normalized, "是谁", "是什么", "名字", "姓名", "身份", "事实", "who", "what", "fact", "name")) {
            types.add(MemoryType.STABLE_FACT);
        }

        if (types.isEmpty()) {
            return memoryProperties.enabledMemoryTypes().stream()
                    .map(MemoryType::valueOf)
                    .toList();
        }
        return List.copyOf(types);
    }

    private String extractSubject(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        Matcher quoted = QUOTED_SUBJECT_PATTERN.matcher(question);
        if (quoted.find()) {
            return quoted.group(1).trim();
        }

        Matcher about = ABOUT_SUBJECT_PATTERN.matcher(question);
        if (about.find()) {
            return about.group(1).trim();
        }

        List<String> candidates = tokenize(question);
        if (candidates.size() == 1 && !isGenericQuestionToken(candidates.get(0))) {
            return candidates.get(0);
        }
        return null;
    }

    private List<String> tokenize(String question) {
        String normalized = question.replaceAll("[\\p{Punct}，。！？；：、]", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private boolean isGenericQuestionToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.equals("what")
                || normalized.equals("who")
                || normalized.equals("why")
                || normalized.equals("how")
                || normalized.equals("which")
                || normalized.equals("prefer")
                || normalized.equals("like")
                || normalized.equals("是谁")
                || normalized.equals("什么")
                || normalized.equals("为什么");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
    }
}
