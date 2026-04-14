package com.example.agentplatform.document.dto;

/**
 * 文本导入结果对象。
 * 返回新文档的 id、业务编码和切块数量。
 */
public record DocumentImportResponse(
        Long documentId,
        String documentCode,
        int chunkCount
) {
}
