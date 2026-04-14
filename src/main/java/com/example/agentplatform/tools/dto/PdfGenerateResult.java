package com.example.agentplatform.tools.dto;

/**
 * PDF 生成结果。
 */
public record PdfGenerateResult(
        String fileName,
        String absolutePath,
        long fileSize
) {
}
