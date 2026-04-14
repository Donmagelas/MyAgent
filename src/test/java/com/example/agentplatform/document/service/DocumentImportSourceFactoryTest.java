package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import com.example.agentplatform.document.dto.DocumentImportRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ETL 导入源工厂的请求映射规则。
 */
class DocumentImportSourceFactoryTest {

    private final DocumentImportSourceFactory factory = new DocumentImportSourceFactory();

    @Test
    void shouldBuildTextImportSource() {
        DocumentImportRequest request = new DocumentImportRequest(
                " project-note ",
                "plain text import",
                "local://note",
                Map.of("category", "intro")
        );

        DocumentImportSource source = factory.fromTextRequest(request);

        assertThat(source.title()).isEqualTo("project-note");
        assertThat(source.sourceType()).isEqualTo("TEXT");
        assertThat(source.sourceUri()).isEqualTo("local://note");
        assertThat(source.contentType()).isEqualTo("text/plain");
        assertThat(source.metadata())
                .containsEntry("category", "intro")
                .containsEntry("ingestType", "TEXT");
    }

    @Test
    void shouldInferJsonFileImportSource() {
        DocumentFileImportRequest request = new DocumentFileImportRequest(
                null,
                null,
                Map.of("scene", "memory"),
                "profile.json",
                "application/json",
                "{\"name\":\"yangbo\"}".getBytes(StandardCharsets.UTF_8)
        );

        DocumentImportSource source = factory.fromFileRequest(request);

        assertThat(source.title()).isEqualTo("profile");
        assertThat(source.sourceType()).isEqualTo("JSON");
        assertThat(source.sourceUri()).isEqualTo("upload://profile.json");
        assertThat(source.originalFilename()).isEqualTo("profile.json");
        assertThat(source.contentType()).isEqualTo("application/json");
        assertThat(source.metadata())
                .containsEntry("scene", "memory")
                .containsEntry("ingestType", "JSON")
                .containsEntry("originalFilename", "profile.json")
                .containsEntry("contentType", "application/json");
    }
}
