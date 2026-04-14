package com.example.agentplatform.document.domain;

import java.util.Map;

/**
 * 文档导入源描述。
 * 统一描述文本导入和文件导入进入 ETL 管线前的源信息。
 */
public record DocumentImportSource(
        String title,
        String sourceType,
        String sourceUri,
        Map<String, Object> metadata,
        String originalFilename,
        String contentType
) {
}
