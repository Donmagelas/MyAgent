package com.example.agentplatform.document.dto;

import java.util.Map;

/**
 * 文件导入请求对象。
 * 供服务层统一描述上传文件的元数据、原始文件信息和字节内容。
 */
public record DocumentFileImportRequest(
        String title,
        String sourceUri,
        Map<String, Object> metadata,
        String originalFilename,
        String contentType,
        byte[] content
) {
}
