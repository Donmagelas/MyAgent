package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据增强转换器。
 * 把导入源信息合并进 Spring AI Document，供后续切块和落库使用。
 */
@Component
public class MetadataEnrichingDocumentTransformer {

    /**
     * 为本次导入创建一个可复用的 transformer。
     */
    public DocumentTransformer create(DocumentImportSource source) {
        return documents -> transform(documents, source);
    }

    /** 为读取到的文档补充统一元数据。 */
    public List<Document> transform(List<Document> documents, DocumentImportSource source) {
        return documents.stream()
                .map(document -> enrich(document, source))
                .toList();
    }

    private Document enrich(Document document, DocumentImportSource source) {
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (source.metadata() != null) {
            mergedMetadata.putAll(source.metadata());
        }
        if (document.getMetadata() != null) {
            mergedMetadata.putAll(document.getMetadata());
        }
        mergedMetadata.put("documentTitle", source.title());
        mergedMetadata.put("sourceType", source.sourceType());
        mergedMetadata.put("sourceUri", source.sourceUri());
        if (source.originalFilename() != null && !source.originalFilename().isBlank()) {
            mergedMetadata.put("originalFilename", source.originalFilename());
        }
        if (source.contentType() != null && !source.contentType().isBlank()) {
            mergedMetadata.put("contentType", source.contentType());
        }
        return Document.builder()
                .id(document.getId())
                .text(document.getText())
                .metadata(mergedMetadata)
                .build();
    }
}
