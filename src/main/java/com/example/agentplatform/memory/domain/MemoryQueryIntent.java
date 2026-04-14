package com.example.agentplatform.memory.domain;

import com.example.agentplatform.memory.dto.MemoryMetadataFilter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 长期记忆查询意图。
 * 描述当前问题对应的记忆类型、主题和最小重要度约束。
 */
public record MemoryQueryIntent(
        List<MemoryType> memoryTypes,
        String subject,
        Integer minImportance,
        MemoryMetadataFilter metadataFilter
) {

    /** 判断当前意图是否至少包含一种可用约束。 */
    public boolean hasMeaningfulConstraint() {
        return (memoryTypes != null && !memoryTypes.isEmpty())
                || (subject != null && !subject.isBlank())
                || minImportance != null
                || (metadataFilter != null && metadataFilter.hasConstraint());
    }

    /** 规范化解析结果，过滤空值、重复值和非法重要度。 */
    public MemoryQueryIntent normalize(List<MemoryType> fallbackTypes) {
        Set<MemoryType> normalizedTypes = new LinkedHashSet<>();
        if (memoryTypes != null) {
            for (MemoryType memoryType : memoryTypes) {
                if (memoryType != null) {
                    normalizedTypes.add(memoryType);
                }
            }
        }

        String normalizedSubject = subject == null || subject.isBlank() ? null : subject.trim();
        Integer normalizedMinImportance = minImportance;
        if (normalizedMinImportance != null) {
            if (normalizedMinImportance < 1) {
                normalizedMinImportance = 1;
            }
            if (normalizedMinImportance > 10) {
                normalizedMinImportance = 10;
            }
        }

        List<MemoryType> effectiveTypes = normalizedTypes.isEmpty()
                ? fallbackTypes
                : List.copyOf(normalizedTypes);

        MemoryMetadataFilter normalizedMetadataFilter = metadataFilter == null
                ? null
                : metadataFilter.normalize();

        return new MemoryQueryIntent(
                effectiveTypes,
                normalizedSubject,
                normalizedMinImportance,
                normalizedMetadataFilter != null && normalizedMetadataFilter.hasConstraint()
                        ? normalizedMetadataFilter
                        : null
        );
    }
}
