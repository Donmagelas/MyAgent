package com.example.agentplatform.rag.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索类型规范化工具。
 * 统一把 retrievalType 排序为固定顺序，避免出现 vector+keyword+rerank 和 keyword+rerank+vector 并存。
 */
public final class RetrievalTypeNormalizer {

    private static final List<String> PREFERRED_ORDER = List.of("vector", "keyword", "rerank");

    private RetrievalTypeNormalizer() {
    }

    /** 规范化多个检索类型字符串。 */
    public static String normalize(String... retrievalTypes) {
        LinkedHashSet<String> typeSet = new LinkedHashSet<>();
        if (retrievalTypes != null) {
            for (String retrievalType : retrievalTypes) {
                addTypes(typeSet, retrievalType);
            }
        }
        return normalize(typeSet);
    }

    /** 规范化检索类型集合。 */
    public static String normalize(Collection<String> retrievalTypes) {
        LinkedHashSet<String> typeSet = new LinkedHashSet<>();
        if (retrievalTypes != null) {
            for (String retrievalType : retrievalTypes) {
                addTypes(typeSet, retrievalType);
            }
        }
        if (typeSet.isEmpty()) {
            return "vector";
        }

        List<String> orderedTypes = new ArrayList<>();
        for (String preferredType : PREFERRED_ORDER) {
            if (typeSet.remove(preferredType)) {
                orderedTypes.add(preferredType);
            }
        }
        orderedTypes.addAll(typeSet);
        return String.join("+", orderedTypes);
    }

    private static void addTypes(Set<String> target, String retrievalType) {
        if (retrievalType == null || retrievalType.isBlank()) {
            return;
        }
        for (String item : retrievalType.split("\\+")) {
            if (!item.isBlank()) {
                target.add(item);
            }
        }
    }
}
