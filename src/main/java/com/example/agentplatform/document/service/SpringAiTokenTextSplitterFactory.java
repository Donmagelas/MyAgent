package com.example.agentplatform.document.service;

import com.example.agentplatform.config.DocumentChunkingProperties;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * Spring AI 切块器工厂。
 * 基于不同文档类型选择更合适的 chunk 大小。
 */
@Component
public class SpringAiTokenTextSplitterFactory {

    private final DocumentChunkingProperties documentChunkingProperties;

    public SpringAiTokenTextSplitterFactory(DocumentChunkingProperties documentChunkingProperties) {
        this.documentChunkingProperties = documentChunkingProperties;
    }

    /**
     * 根据文档类型创建本次导入使用的 TokenTextSplitter。
     */
    public TokenTextSplitter create(String sourceType) {
        ChunkingSetting setting = resolveSetting(sourceType);
        return TokenTextSplitter.builder()
                .withChunkSize(setting.chunkSize())
                .withMinChunkSizeChars(Math.max(50, setting.overlapChars()))
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }

    /**
     * 返回当前文档类型使用的 overlap 字符长度。
     */
    public int resolveOverlapChars(String sourceType) {
        return resolveSetting(sourceType).overlapChars();
    }

    private ChunkingSetting resolveSetting(String sourceType) {
        return switch (sourceType) {
            case "MARKDOWN" -> new ChunkingSetting(
                    documentChunkingProperties.markdown().chunkSize(),
                    documentChunkingProperties.markdown().overlapChars()
            );
            case "JSON" -> new ChunkingSetting(
                    documentChunkingProperties.json().chunkSize(),
                    documentChunkingProperties.json().overlapChars()
            );
            default -> new ChunkingSetting(
                    documentChunkingProperties.text().chunkSize(),
                    documentChunkingProperties.text().overlapChars()
            );
        };
    }

    /**
     * 文档切分参数。
     */
    private record ChunkingSetting(
            int chunkSize,
            int overlapChars
    ) {
    }
}
