package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Spring AI Reader 工厂是否按文件类型选择正确的 Reader。
 */
class SpringAiDocumentReaderFactoryTest {

    private final SpringAiDocumentReaderFactory factory = new SpringAiDocumentReaderFactory();

    @Test
    void shouldCreateTextReaderForInlineText() {
        DocumentReader reader = factory.createForText("first paragraph\nsecond paragraph");
        List<Document> documents = reader.read();

        assertThat(reader).isInstanceOf(TextReader.class);
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("first paragraph");
    }

    @Test
    void shouldCreateJsonReaderForJsonFile() {
        DocumentImportSource source = new DocumentImportSource(
                "profile",
                "JSON",
                "upload://profile.json",
                Map.of("ingestType", "JSON"),
                "profile.json",
                "application/json"
        );
        DocumentFileImportRequest request = new DocumentFileImportRequest(
                "profile",
                "upload://profile.json",
                Map.of("ingestType", "JSON"),
                "profile.json",
                "application/json",
                "{\"user\":{\"name\":\"yangbo\"}}".getBytes(StandardCharsets.UTF_8)
        );

        DocumentReader reader = factory.createForFile(source, request);
        List<Document> documents = reader.read();

        assertThat(reader).isInstanceOf(JsonReader.class);
        assertThat(documents).isNotEmpty();
    }

    @Test
    void shouldCreateTextReaderForMarkdownFile() {
        DocumentImportSource source = new DocumentImportSource(
                "guide",
                "MARKDOWN",
                "upload://guide.md",
                Map.of("ingestType", "MARKDOWN"),
                "guide.md",
                "text/markdown"
        );
        DocumentFileImportRequest request = new DocumentFileImportRequest(
                "guide",
                "upload://guide.md",
                Map.of("ingestType", "MARKDOWN"),
                "guide.md",
                "text/markdown",
                "# title\nbody".getBytes(StandardCharsets.UTF_8)
        );

        DocumentReader reader = factory.createForFile(source, request);
        List<Document> documents = reader.read();

        assertThat(reader).isInstanceOf(TextReader.class);
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("# title");
    }
}
