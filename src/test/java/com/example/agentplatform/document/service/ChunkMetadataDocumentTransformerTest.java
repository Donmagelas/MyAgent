package com.example.agentplatform.document.service;

import com.example.agentplatform.config.DocumentChunkingProperties;
import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.document.domain.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * chunk 元数据增强测试。
 * 验证 chunk 标题、摘要和来源元数据会在增强阶段被补齐。
 */
class ChunkMetadataDocumentTransformerTest {

    @Test
    void shouldEnhanceChunkTitleAndSummary() {
        ChunkMetadataDocumentTransformer transformer = new ChunkMetadataDocumentTransformer(
                new DocumentChunkingProperties(
                        "section-aware",
                        new DocumentChunkingProperties.Markdown(true, 560, 60),
                        new DocumentChunkingProperties.Json(true, 420, 40),
                        new DocumentChunkingProperties.Text(true, 700, 100),
                        new DocumentChunkingProperties.Enhancement(true, true, 80)
                )
        );
        KnowledgeDocument knowledgeDocument = new KnowledgeDocument(
                1L,
                "doc-001",
                "RAG Guide",
                "MARKDOWN",
                "upload://rag.md",
                "ACTIVE",
                Map.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        DocumentImportSource source = new DocumentImportSource(
                "RAG Guide",
                "MARKDOWN",
                "upload://rag.md",
                Map.of(),
                "rag.md",
                "text/markdown"
        );
        Document chunk = Document.builder()
                .text("混合检索会同时执行向量检索和关键词检索，然后再进行融合与重排。")
                .metadata(Map.of(
                        DocumentMetadataKeys.SECTION_TITLE, "混合检索",
                        DocumentMetadataKeys.SECTION_PATH, "RAG / 混合检索",
                        DocumentMetadataKeys.HEADING_TITLE, "混合检索"
                ))
                .build();

        List<Document> enhanced = transformer.transform(List.of(chunk), knowledgeDocument, source);

        assertEquals(1, enhanced.size());
        Map<String, Object> metadata = enhanced.get(0).getMetadata();
        assertEquals("混合检索", metadata.get(DocumentMetadataKeys.CHUNK_TITLE));
        assertTrue(String.valueOf(metadata.get(DocumentMetadataKeys.CHUNK_SUMMARY)).contains("混合检索"));
        assertEquals("RAG Guide", metadata.get(DocumentMetadataKeys.DOCUMENT_TITLE));
        assertEquals(0, metadata.get(DocumentMetadataKeys.CHUNK_INDEX));
    }

    @Test
    void shouldKeepJsonPathSummaryIntact() {
        ChunkMetadataDocumentTransformer transformer = new ChunkMetadataDocumentTransformer(
                new DocumentChunkingProperties(
                        "section-aware",
                        new DocumentChunkingProperties.Markdown(true, 560, 60),
                        new DocumentChunkingProperties.Json(true, 420, 40),
                        new DocumentChunkingProperties.Text(true, 700, 100),
                        new DocumentChunkingProperties.Enhancement(true, true, 80)
                )
        );
        KnowledgeDocument knowledgeDocument = new KnowledgeDocument(
                2L,
                "doc-002",
                "Json Guide",
                "JSON",
                "upload://guide.json",
                "ACTIVE",
                Map.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        DocumentImportSource source = new DocumentImportSource(
                "Json Guide",
                "JSON",
                "upload://guide.json",
                Map.of(),
                "guide.json",
                "application/json"
        );
        Document chunk = Document.builder()
                .text("project.name: agent-platform")
                .metadata(Map.of(
                        DocumentMetadataKeys.JSON_PATH, "project.name",
                        DocumentMetadataKeys.SECTION_TITLE, "project.name",
                        DocumentMetadataKeys.SECTION_PATH, "project.name"
                ))
                .build();

        List<Document> enhanced = transformer.transform(List.of(chunk), knowledgeDocument, source);

        Map<String, Object> metadata = enhanced.get(0).getMetadata();
        assertEquals("project.name", metadata.get(DocumentMetadataKeys.CHUNK_TITLE));
        assertEquals("project.name: agent-platform", metadata.get(DocumentMetadataKeys.CHUNK_SUMMARY));
    }
}
