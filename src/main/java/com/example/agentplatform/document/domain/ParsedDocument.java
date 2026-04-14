package com.example.agentplatform.document.domain;

import java.util.Map;

/**
 * 规范化后的解析文档对象。
 * 作为解析阶段和切块阶段之间的传递对象。
 */
public record ParsedDocument(
        String title,
        String content,
        String sourceType,
        String sourceUri,
        Map<String, Object> metadata
) {
}
