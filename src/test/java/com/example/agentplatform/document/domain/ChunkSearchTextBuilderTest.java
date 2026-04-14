package com.example.agentplatform.document.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSearchTextBuilderTest {

    @Test
    void shouldCombineChunkTitleSummaryAndContentInOrder() {
        String searchText = ChunkSearchTextBuilder.build(
                "正文内容",
                Map.of(
                        DocumentMetadataKeys.CHUNK_TITLE, "分块标题",
                        DocumentMetadataKeys.CHUNK_SUMMARY, "分块摘要"
                )
        );

        assertThat(searchText).isEqualTo("分块标题\n分块摘要\n正文内容");
    }

    @Test
    void shouldSkipBlankMetadataAndKeepContent() {
        String searchText = ChunkSearchTextBuilder.build(
                "正文内容",
                Map.of(
                        DocumentMetadataKeys.CHUNK_TITLE, " ",
                        DocumentMetadataKeys.CHUNK_SUMMARY, ""
                )
        );

        assertThat(searchText).isEqualTo("正文内容");
    }
}
