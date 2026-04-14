package com.example.agentplatform.document.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * chunk FTS 检索文本构造器。
 * 将标题、摘要和正文合并成统一检索文本，供 PostgreSQL FTS 建索引使用。
 */
public final class ChunkSearchTextBuilder {

    private ChunkSearchTextBuilder() {
    }

    /**
     * 基于 chunk 正文和 metadata 中的增强字段生成可检索文本。
     */
    public static String build(String content, Map<String, Object> metadata) {
        List<String> parts = new ArrayList<>(3);
        append(parts, readMetadata(metadata, DocumentMetadataKeys.CHUNK_TITLE));
        append(parts, readMetadata(metadata, DocumentMetadataKeys.CHUNK_SUMMARY));
        append(parts, content);
        return String.join("\n", parts);
    }

    private static void append(List<String> parts, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private static String readMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }
}
