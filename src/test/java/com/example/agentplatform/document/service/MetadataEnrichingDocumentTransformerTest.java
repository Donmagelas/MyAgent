package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ETL 元数据增强步骤是否保留并补齐关键字段。
 */
class MetadataEnrichingDocumentTransformerTest {

    private final MetadataEnrichingDocumentTransformer transformer = new MetadataEnrichingDocumentTransformer();

    @Test
    void shouldMergeSourceAndDocumentMetadata() {
        Document sourceDocument = Document.builder()
                .id("doc-1")
                .text("sample content")
                .metadata(Map.of("sourceType", "OVERRIDE", "readerTag", "text-reader"))
                .build();
        DocumentImportSource source = new DocumentImportSource(
                "project-doc",
                "MARKDOWN",
                "upload://guide.md",
                Map.of("tenant", "demo", "sourceType", "SOURCE"),
                "guide.md",
                "text/markdown"
        );

        List<Document> documents = transformer.transform(List.of(sourceDocument), source);

        assertThat(documents).hasSize(1);
        Document enriched = documents.get(0);
        assertThat(enriched.getText()).isEqualTo("sample content");
        assertThat(enriched.getMetadata())
                .containsEntry("tenant", "demo")
                .containsEntry("readerTag", "text-reader")
                .containsEntry("documentTitle", "project-doc")
                .containsEntry("sourceType", "MARKDOWN")
                .containsEntry("sourceUri", "upload://guide.md")
                .containsEntry("originalFilename", "guide.md")
                .containsEntry("contentType", "text/markdown");
    }
}
