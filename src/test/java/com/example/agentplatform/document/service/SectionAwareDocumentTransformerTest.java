package com.example.agentplatform.document.service;

import com.example.agentplatform.config.DocumentChunkingProperties;
import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 结构感知切分测试。
 * 验证 Markdown 标题切分和 JSON 字段语义切分是否按预期生效。
 */
class SectionAwareDocumentTransformerTest {

    @Test
    void shouldSplitMarkdownByHeading() {
        SectionAwareDocumentTransformer transformer = new SectionAwareDocumentTransformer(
                new ObjectMapper(),
                new DocumentChunkingProperties(
                        "section-aware",
                        new DocumentChunkingProperties.Markdown(true, 560, 60),
                        new DocumentChunkingProperties.Json(true, 420, 40),
                        new DocumentChunkingProperties.Text(true, 700, 100),
                        new DocumentChunkingProperties.Enhancement(true, true, 140)
                )
        );
        DocumentImportSource source = new DocumentImportSource(
                "Markdown Guide",
                "MARKDOWN",
                "upload://guide.md",
                Map.of(),
                "guide.md",
                "text/markdown"
        );
        Document document = Document.builder()
                .text("""
                        # 总览
                        这是概览部分。
                        ## 安装
                        这是安装说明。
                        ## 使用
                        这是使用说明。
                        """)
                .metadata(Map.of())
                .build();

        List<Document> sections = transformer.transform(List.of(document), source);

        assertEquals(3, sections.size());
        assertEquals("总览", sections.get(0).getMetadata().get(DocumentMetadataKeys.SECTION_TITLE));
        assertEquals("总览 / 安装", sections.get(1).getMetadata().get(DocumentMetadataKeys.SECTION_PATH));
        assertEquals("安装", sections.get(1).getMetadata().get(DocumentMetadataKeys.HEADING_TITLE));
        assertEquals(2, sections.get(1).getMetadata().get(DocumentMetadataKeys.HEADING_LEVEL));
    }

    @Test
    void shouldSplitJsonBySemanticPath() {
        SectionAwareDocumentTransformer transformer = new SectionAwareDocumentTransformer(
                new ObjectMapper(),
                new DocumentChunkingProperties(
                        "section-aware",
                        new DocumentChunkingProperties.Markdown(true, 560, 60),
                        new DocumentChunkingProperties.Json(true, 420, 40),
                        new DocumentChunkingProperties.Text(true, 700, 100),
                        new DocumentChunkingProperties.Enhancement(true, true, 140)
                )
        );
        DocumentImportSource source = new DocumentImportSource(
                "Json Guide",
                "JSON",
                "upload://guide.json",
                Map.of(),
                "guide.json",
                "application/json"
        );
        Document document = Document.builder()
                .text("""
                        {
                          "project": {
                            "name": "agent-platform",
                            "version": "v1"
                          },
                          "owner": "yangbo"
                        }
                        """)
                .metadata(Map.of())
                .build();

        List<Document> sections = transformer.transform(List.of(document), source);

        assertFalse(sections.isEmpty());
        assertEquals("project.name", sections.get(0).getMetadata().get(DocumentMetadataKeys.JSON_PATH));
        assertEquals("project.name", sections.get(0).getMetadata().get(DocumentMetadataKeys.SECTION_TITLE));
    }

    @Test
    void shouldSplitJsonWithBomBySemanticPath() {
        SectionAwareDocumentTransformer transformer = new SectionAwareDocumentTransformer(
                new ObjectMapper(),
                new DocumentChunkingProperties(
                        "section-aware",
                        new DocumentChunkingProperties.Markdown(true, 560, 60),
                        new DocumentChunkingProperties.Json(true, 420, 40),
                        new DocumentChunkingProperties.Text(true, 700, 100),
                        new DocumentChunkingProperties.Enhancement(true, true, 140)
                )
        );
        DocumentImportSource source = new DocumentImportSource(
                "Json Guide",
                "JSON",
                "upload://guide.json",
                Map.of(),
                "guide.json",
                "application/json"
        );
        Document document = Document.builder()
                .text("\uFEFF{\"owner\":\"yangbo\",\"project\":{\"name\":\"agent-platform\"}}")
                .metadata(Map.of())
                .build();

        List<Document> sections = transformer.transform(List.of(document), source);

        assertEquals("owner", sections.get(0).getMetadata().get(DocumentMetadataKeys.JSON_PATH));
        assertEquals("project.name", sections.get(1).getMetadata().get(DocumentMetadataKeys.JSON_PATH));
    }
}