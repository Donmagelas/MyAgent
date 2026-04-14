package com.example.agentplatform.document.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 纯文本知识导入请求对象。
 * metadata 为可选字段，预留给后续过滤和治理规则。
 */
public record DocumentImportRequest(
        @NotBlank String title,
        @NotBlank String content,
        String sourceUri,
        Map<String, Object> metadata
) {
}
