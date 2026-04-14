package com.example.agentplatform.document.service;

import com.example.agentplatform.config.DocumentChunkingProperties;
import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.domain.DocumentMetadataKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知文档转换器。
 * 在进入 token 切分之前，先按 Markdown 标题、JSON 字段路径或文本段落拆成更稳定的 section。
 */
@Component
public class SectionAwareDocumentTransformer {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern PARAGRAPH_SPLIT_PATTERN = Pattern.compile("\\n\\s*\\n+");

    private final ObjectMapper objectMapper;
    private final DocumentChunkingProperties documentChunkingProperties;

    public SectionAwareDocumentTransformer(
            ObjectMapper objectMapper,
            DocumentChunkingProperties documentChunkingProperties
    ) {
        this.objectMapper = objectMapper;
        this.documentChunkingProperties = documentChunkingProperties;
    }

    /**
     * 为当前导入请求创建一个可复用的 section 切分器。
     */
    public DocumentTransformer create(DocumentImportSource source) {
        return documents -> transform(documents, source);
    }

    /**
     * 按文档类型执行结构感知切分。
     */
    public List<Document> transform(List<Document> documents, DocumentImportSource source) {
        List<Document> transformed = new ArrayList<>();
        for (Document document : documents) {
            transformed.addAll(transformSingle(document, source));
        }
        return transformed.isEmpty() ? documents : transformed;
    }

    private List<Document> transformSingle(Document document, DocumentImportSource source) {
        return switch (source.sourceType()) {
            case "MARKDOWN" -> documentChunkingProperties.markdown().headingAware()
                    ? splitMarkdown(document, source)
                    : List.of(document);
            case "JSON" -> documentChunkingProperties.json().semanticAware()
                    ? splitJson(document)
                    : List.of(document);
            case "TEXT", "TXT" -> documentChunkingProperties.text().paragraphAware()
                    ? splitParagraphs(document, source)
                    : List.of(document);
            default -> List.of(document);
        };
    }

    private List<Document> splitMarkdown(Document document, DocumentImportSource source) {
        String normalizedText = normalizeText(document.getText());
        List<Document> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        String currentHeading = source.title();
        int currentLevel = 0;
        StringBuilder currentContent = new StringBuilder();
        int sectionIndex = 0;
        boolean hasHeading = false;

        for (String line : normalizedText.split("\\n", -1)) {
            Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                hasHeading = true;
                sectionIndex = flushMarkdownSection(
                        sections,
                        document,
                        currentHeading,
                        currentLevel,
                        buildSectionPath(headingStack, currentHeading),
                        currentContent,
                        sectionIndex
                );
                currentLevel = matcher.group(1).length();
                currentHeading = matcher.group(2).trim();
                trimHeadingStack(headingStack, currentLevel);
                headingStack.add(currentHeading);
                currentContent = new StringBuilder();
                currentContent.append(currentHeading).append("\n\n");
            }
            else {
                currentContent.append(line).append("\n");
            }
        }

        flushMarkdownSection(
                sections,
                document,
                currentHeading,
                currentLevel,
                buildSectionPath(headingStack, currentHeading),
                currentContent,
                sectionIndex
        );

        if (!hasHeading || sections.isEmpty()) {
            return splitParagraphs(document, source);
        }
        return sections;
    }

    private int flushMarkdownSection(
            List<Document> sections,
            Document baseDocument,
            String headingTitle,
            int headingLevel,
            String sectionPath,
            StringBuilder currentContent,
            int sectionIndex
    ) {
        String text = currentContent.toString().trim();
        if (text.isBlank()) {
            return sectionIndex;
        }
        Map<String, Object> metadata = baseMetadata(baseDocument, sectionIndex, "markdown-section", headingTitle, sectionPath);
        metadata.put(DocumentMetadataKeys.HEADING_TITLE, headingTitle);
        metadata.put(DocumentMetadataKeys.HEADING_LEVEL, headingLevel);
        sections.add(buildDocument(baseDocument, text, metadata));
        return sectionIndex + 1;
    }

    private List<Document> splitParagraphs(Document document, DocumentImportSource source) {
        String normalizedText = normalizeText(document.getText());
        String[] parts = PARAGRAPH_SPLIT_PATTERN.split(normalizedText);
        List<Document> sections = new ArrayList<>();
        int sectionIndex = 0;
        for (String part : parts) {
            String text = part.trim();
            if (text.isBlank()) {
                continue;
            }
            String sectionTitle = deriveParagraphTitle(text, source.title(), sectionIndex);
            Map<String, Object> metadata = baseMetadata(
                    document,
                    sectionIndex,
                    "text-section",
                    sectionTitle,
                    source.title() + " / " + sectionTitle
            );
            sections.add(buildDocument(document, text, metadata));
            sectionIndex++;
        }
        return sections.isEmpty() ? List.of(document) : sections;
    }

    private List<Document> splitJson(Document document) {
        try {
            JsonNode root = objectMapper.readTree(normalizeText(document.getText()));
            List<Document> sections = new ArrayList<>();
            collectJsonSections(document, root, "", 0, sections);
            return sections.isEmpty() ? List.of(document) : sections;
        }
        catch (Exception exception) {
            return List.of(document);
        }
    }

    private int collectJsonSections(
            Document baseDocument,
            JsonNode node,
            String path,
            int sectionIndex,
            List<Document> sections
    ) {
        if (node == null || node.isNull()) {
            return sectionIndex;
        }

        if (node.isValueNode()) {
            String sectionTitle = path.isBlank() ? "root" : path;
            String text = path.isBlank() ? node.asText() : path + ": " + node.asText();
            Map<String, Object> metadata = baseMetadata(
                    baseDocument,
                    sectionIndex,
                    "json-field",
                    sectionTitle,
                    sectionTitle
            );
            metadata.put(DocumentMetadataKeys.JSON_PATH, path);
            sections.add(buildDocument(baseDocument, text, metadata));
            return sectionIndex + 1;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                return sectionIndex;
            }
            int index = 0;
            for (JsonNode item : node) {
                String itemPath = path + "[" + index + "]";
                sectionIndex = collectJsonSections(baseDocument, item, itemPath, sectionIndex, sections);
                index++;
            }
            return sectionIndex;
        }

        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String fieldPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
                sectionIndex = collectJsonSections(baseDocument, field.getValue(), fieldPath, sectionIndex, sections);
            }
            return sectionIndex;
        }

        return sectionIndex;
    }

    private Map<String, Object> baseMetadata(
            Document baseDocument,
            int sectionIndex,
            String sectionType,
            String sectionTitle,
            String sectionPath
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (baseDocument.getMetadata() != null) {
            metadata.putAll(baseDocument.getMetadata());
        }
        metadata.put(DocumentMetadataKeys.SECTION_INDEX, sectionIndex);
        metadata.put(DocumentMetadataKeys.SECTION_TYPE, sectionType);
        metadata.put(DocumentMetadataKeys.SECTION_TITLE, sectionTitle);
        metadata.put(DocumentMetadataKeys.SECTION_PATH, sectionPath);
        return metadata;
    }

    private Document buildDocument(Document baseDocument, String text, Map<String, Object> metadata) {
        return Document.builder()
                .id(baseDocument.getId())
                .text(text)
                .metadata(metadata)
                .build();
    }

    private void trimHeadingStack(List<String> headingStack, int level) {
        while (headingStack.size() >= level) {
            headingStack.remove(headingStack.size() - 1);
        }
    }

    private String buildSectionPath(List<String> headingStack, String currentHeading) {
        Deque<String> parts = new ArrayDeque<>();
        for (String heading : headingStack) {
            if (heading != null && !heading.isBlank()) {
                parts.addLast(heading);
            }
        }
        if (parts.isEmpty() && currentHeading != null && !currentHeading.isBlank()) {
            parts.addLast(currentHeading);
        }
        return String.join(" / ", parts);
    }

    private String deriveParagraphTitle(String text, String documentTitle, int sectionIndex) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return documentTitle + " 段落 " + (sectionIndex + 1);
        }
        int punctuationIndex = findSentenceEnd(normalized);
        String title = punctuationIndex > 0 ? normalized.substring(0, punctuationIndex) : normalized;
        if (title.length() > 32) {
            title = title.substring(0, 32).trim();
        }
        return title.isBlank() ? documentTitle + " 段落 " + (sectionIndex + 1) : title;
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

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text;
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }
}
