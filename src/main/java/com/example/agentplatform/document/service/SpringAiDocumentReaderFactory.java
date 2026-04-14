package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Spring AI 文档读取器工厂。
 * 优先使用 Spring AI 已有的 DocumentReader，把输入统一转换成 Document。
 */
@Component
public class SpringAiDocumentReaderFactory {

    /**
     * 为纯文本导入创建 Reader。
     */
    public DocumentReader createForText(String content) {
        Resource resource = new NamedByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "inline.txt");
        return new TextReader(resource);
    }

    /**
     * 为上传文件创建 Reader。
     */
    public DocumentReader createForFile(DocumentImportSource source, DocumentFileImportRequest request) {
        Resource resource = new NamedByteArrayResource(request.content(), request.originalFilename());
        // JsonReader 会把 JSON 渲染成 Map 风格文本，不利于后续按字段路径做语义切分。
        // 这里仍然复用 Spring AI 的 TextReader，保留原始 JSON 文本，再交给 section-aware 转换器处理。
        return new TextReader(resource);
    }
}
