package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.example.agentplatform.document.domain.DocumentImportSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * section-aware 切块服务。
 * 先在 section 内执行 Spring AI TokenTextSplitter，再按配置追加轻量 overlap。
 */
@Component
public class SectionAwareChunkingService {

    private final SpringAiTokenTextSplitterFactory springAiTokenTextSplitterFactory;

    public SectionAwareChunkingService(SpringAiTokenTextSplitterFactory springAiTokenTextSplitterFactory) {
        this.springAiTokenTextSplitterFactory = springAiTokenTextSplitterFactory;
    }

    /**
     * 对 section 文档执行切块。
     */
    public List<Document> split(List<Document> sectionDocuments, DocumentImportSource source) {
        TokenTextSplitter splitter = springAiTokenTextSplitterFactory.create(source.sourceType());
        int overlapChars = springAiTokenTextSplitterFactory.resolveOverlapChars(source.sourceType());

        List<Document> chunkDocuments = new ArrayList<>();
        for (Document sectionDocument : sectionDocuments) {
            List<Document> chunks = splitter.apply(List.of(sectionDocument));
            chunkDocuments.addAll(applyOverlap(chunks, overlapChars));
        }
        return chunkDocuments;
    }

    private List<Document> applyOverlap(List<Document> chunks, int overlapChars) {
        if (overlapChars <= 0 || chunks.size() < 2) {
            return chunks;
        }
        List<Document> overlappedChunks = new ArrayList<>(chunks.size());
        Document previousChunk = null;
        for (Document chunk : chunks) {
            if (previousChunk == null) {
                overlappedChunks.add(chunk);
                previousChunk = chunk;
                continue;
            }

            String overlapPrefix = tail(previousChunk.getText(), overlapChars);
            String mergedText = overlapPrefix.isBlank()
                    ? chunk.getText()
                    : overlapPrefix + "\n" + chunk.getText();
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (chunk.getMetadata() != null) {
                metadata.putAll(chunk.getMetadata());
            }
            metadata.put(DocumentMetadataKeys.OVERLAP_CHARS, overlapChars);
            Document overlappedChunk = chunk.mutate()
                    .text(mergedText)
                    .metadata(metadata)
                    .build();
            overlappedChunks.add(overlappedChunk);
            previousChunk = chunk;
        }
        return overlappedChunks;
    }

    private String tail(String text, int overlapChars) {
        if (text == null || text.isBlank() || overlapChars <= 0) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= overlapChars) {
            return normalized;
        }
        int start = normalized.length() - overlapChars;
        int wordBoundary = normalized.indexOf(' ', start);
        if (wordBoundary >= 0 && wordBoundary < normalized.length() - 1) {
            return normalized.substring(wordBoundary + 1).trim();
        }
        return normalized.substring(start).trim();
    }
}
