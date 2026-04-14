package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryExtractionCandidate;
import com.example.agentplatform.memory.domain.MemoryType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 自动长期记忆提炼候选项后处理器。
 * 用于在模型输出后做轻量纠偏，避免身份、位置等稳定事实被误归类为偏好。
 */
@Component
public class MemoryExtractionCandidatePostProcessor {

    private static final Set<String> STABLE_FACT_SUBJECT_HINTS = Set.of(
            "user_name",
            "name",
            "real_name",
            "nickname",
            "user_identity",
            "identity",
            "user_location",
            "location",
            "city",
            "country",
            "residence",
            "role",
            "organization",
            "company"
    );

    private static final Set<String> STABLE_FACT_CONTENT_HINTS = Set.of(
            "name is",
            "nickname is",
            "lives in",
            "live in",
            "resides in",
            "located in",
            "is based in",
            "works at",
            "role is",
            "user name",
            "user lives",
            "user resides",
            "名字是",
            "昵称是",
            "住在",
            "位于",
            "角色是",
            "公司是"
    );

    /**
     * 归一化候选项，并在必要时纠正 memoryType。
     */
    public MemoryExtractionCandidate normalize(MemoryExtractionCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        String subject = trim(candidate.subject());
        String content = trim(candidate.content());
        String summary = trim(candidate.summaryText());
        int importance = candidate.importance() == null ? 5 : Math.max(1, Math.min(candidate.importance(), 10));
        MemoryType normalizedType = normalizeMemoryType(candidate.memoryType(), subject, content);
        return new MemoryExtractionCandidate(
                normalizedType,
                subject,
                content,
                importance,
                summary
        );
    }

    private MemoryType normalizeMemoryType(MemoryType currentType, String subject, String content) {
        if (currentType != MemoryType.USER_PREFERENCE) {
            return currentType;
        }
        String normalizedSubject = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        String normalizedContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (matchesAny(normalizedSubject, STABLE_FACT_SUBJECT_HINTS)
                || matchesAny(normalizedContent, STABLE_FACT_CONTENT_HINTS)) {
            return MemoryType.STABLE_FACT;
        }
        return currentType;
    }

    private boolean matchesAny(String text, Set<String> hints) {
        return hints.stream().anyMatch(text::contains);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
