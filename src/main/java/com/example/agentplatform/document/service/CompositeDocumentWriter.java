package com.example.agentplatform.document.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 复合文档写入器。
 * 按顺序执行多个 DocumentWriter，用于把同一批 chunk 同步写入多个存储。
 */
@Component
public class CompositeDocumentWriter {

    /**
     * 创建绑定了具体 writer 列表的复合写入器。
     */
    public DocumentWriter bind(List<DocumentWriter> writers) {
        return documents -> {
            for (DocumentWriter writer : writers) {
                writer.write(documents);
            }
        };
    }
}
