package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import com.example.agentplatform.document.dto.DocumentImportRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文档导入源工厂。
 * 负责把不同入口的请求统一映射为 ETL 管线可消费的源描述。
 */
@Component
public class DocumentImportSourceFactory {

    /** 根据纯文本导入请求构建导入源。 */
    public DocumentImportSource fromTextRequest(DocumentImportRequest request) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.putIfAbsent("ingestType", "TEXT");
        return new DocumentImportSource(
                request.title().trim(),
                "TEXT",
                request.sourceUri(),
                metadata,
                null,
                "text/plain"
        );
    }

    /** 根据文件导入请求构建导入源。 */
    public DocumentImportSource fromFileRequest(DocumentFileImportRequest request) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        String sourceType = inferSourceType(request.originalFilename(), request.contentType());
        metadata.putIfAbsent("ingestType", sourceType);
        metadata.putIfAbsent("originalFilename", request.originalFilename());
        metadata.putIfAbsent("contentType", request.contentType());
        return new DocumentImportSource(
                resolveTitle(request.title(), request.originalFilename()),
                sourceType,
                resolveSourceUri(request.sourceUri(), request.originalFilename()),
                metadata,
                request.originalFilename(),
                request.contentType()
        );
    }

    private String inferSourceType(String originalFilename, String contentType) {
        String lowerFilename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".json") || lowerContentType.contains("json")) {
            return "JSON";
        }
        if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown") || lowerContentType.contains("markdown")) {
            return "MARKDOWN";
        }
        return "TXT";
    }

    private String resolveTitle(String explicitTitle, String originalFilename) {
        if (explicitTitle != null && !explicitTitle.isBlank()) {
            return explicitTitle.trim();
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            return "uploaded-document";
        }
        int extensionIndex = originalFilename.lastIndexOf('.');
        return extensionIndex <= 0 ? originalFilename : originalFilename.substring(0, extensionIndex);
    }

    private String resolveSourceUri(String sourceUri, String originalFilename) {
        if (sourceUri != null && !sourceUri.isBlank()) {
            return sourceUri.trim();
        }
        return originalFilename == null || originalFilename.isBlank()
                ? "upload://document"
                : "upload://" + originalFilename;
    }
}
