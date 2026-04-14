package com.example.agentplatform.document.service;

import com.example.agentplatform.config.DocumentChunkingProperties;
import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.document.domain.KnowledgeDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * chunk 元数据增强器。
 * 为每个 chunk 注入业务文档标识、section 信息以及规则版标题和摘要。
 */
@Component
public class ChunkMetadataDocumentTransformer {

    private final DocumentChunkingProperties documentChunkingProperties;

    public ChunkMetadataDocumentTransformer(DocumentChunkingProperties documentChunkingProperties) {
        this.documentChunkingProperties = documentChunkingProperties;
    }

    /**
     * 为本次导入创建一个可复用的 transformer。
     */
    public DocumentTransformer create(KnowledgeDocument document, DocumentImportSource source) {
        return inputDocuments -> transform(inputDocuments, document, source);
    }

    /**
     * 为切块结果补齐统一 metadata 和稳定的向量文档 ID。
     */
    public List<Document> transform(
            List<Document> inputDocuments,
            KnowledgeDocument document,
            DocumentImportSource source
    ) {
        return java.util.stream.IntStream.range(0, inputDocuments.size())
                .mapToObj(index -> enrich(inputDocuments.get(index), document, source, index))
                .toList();
    }

    private Document enrich(
            Document chunkDocument,
            KnowledgeDocument document,
            DocumentImportSource source,
            int chunkIndex
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (chunkDocument.getMetadata() != null) {
            metadata.putAll(chunkDocument.getMetadata());
        }
        metadata.put(DocumentMetadataKeys.DOCUMENT_ID, document.id());
        metadata.put(DocumentMetadataKeys.DOCUMENT_CODE, document.documentCode());
        metadata.put(DocumentMetadataKeys.DOCUMENT_TITLE, document.title());
        metadata.put(DocumentMetadataKeys.SOURCE_TYPE, source.sourceType());
        metadata.put(DocumentMetadataKeys.SOURCE_URI, source.sourceUri());
        metadata.put(DocumentMetadataKeys.CHUNK_INDEX, chunkIndex);

        if (documentChunkingProperties.enhancement().chunkTitleEnabled()) {
            metadata.put(DocumentMetadataKeys.CHUNK_TITLE, deriveChunkTitle(metadata, document.title(), chunkIndex));
        }
        if (documentChunkingProperties.enhancement().chunkSummaryEnabled()) {
            metadata.put(
                    DocumentMetadataKeys.CHUNK_SUMMARY,
                    deriveChunkSummary(chunkDocument.getText(), metadata, documentChunkingProperties.enhancement().summaryMaxLength())
            );
        }

        return Document.builder()
                .id(buildVectorDocumentId(document.id(), chunkIndex))
                .text(chunkDocument.getText())
                .metadata(metadata)
                .build();
    }

    private String deriveChunkTitle(Map<String, Object> metadata, String documentTitle, int chunkIndex) {
        String headingTitle = readString(metadata, DocumentMetadataKeys.HEADING_TITLE);
        if (!headingTitle.isBlank()) {
            return headingTitle;
        }
        String sectionTitle = readString(metadata, DocumentMetadataKeys.SECTION_TITLE);
        if (!sectionTitle.isBlank()) {
            return sectionTitle;
        }
        String jsonPath = readString(metadata, DocumentMetadataKeys.JSON_PATH);
        if (!jsonPath.isBlank()) {
            return jsonPath;
        }
        String sectionPath = readString(metadata, DocumentMetadataKeys.SECTION_PATH);
        if (!sectionPath.isBlank()) {
            return sectionPath;
        }
        return documentTitle + " - 片段 " + (chunkIndex + 1);
    }

    private String deriveChunkSummary(String content, Map<String, Object> metadata, int maxLength) {
        String normalized = normalizeContent(content);
        if (normalized.isBlank()) {
            return "";
        }
        if (!readString(metadata, DocumentMetadataKeys.JSON_PATH).isBlank()) {
            return truncate(normalized, maxLength);
        }
        int end = findSentenceEnd(normalized);
        String summary = end > 0 ? normalized.substring(0, end) : normalized;
        summary = truncate(summary, maxLength);
        String title = readString(metadata, DocumentMetadataKeys.CHUNK_TITLE);
        if (title.isBlank() || summary.startsWith(title)) {
            return summary;
        }
        return title + "：" + summary;
    }

    private int findSentenceEnd(String text) {
        int index = Integer.MAX_VALUE;
        for (String delimiter : List.of("。", "！", "？", ".", "!", "?", ";", "；")) {
            int current = text.indexOf(delimiter);
            if (current >= 0) {
                index = Math.min(index, current + 1);
            }
        }
        return index == Integer.MAX_VALUE ? -1 : index;
    }

    private String readString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content;
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength).trim();
    }

    private String buildVectorDocumentId(Long documentId, int chunkIndex) {
        String rawId = "knowledge-" + documentId + "-" + chunkIndex;
        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
