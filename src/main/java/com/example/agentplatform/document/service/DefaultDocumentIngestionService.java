package com.example.agentplatform.document.service;

import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import com.example.agentplatform.document.dto.DocumentImportRequest;
import com.example.agentplatform.document.dto.DocumentImportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 默认文档导入流程。
 * 负责一篇文本的解析、切块、向量化和持久化全流程。
 */
@Service
public class DefaultDocumentIngestionService implements DocumentIngestionService {

    private final DocumentImportSourceFactory documentImportSourceFactory;
    private final SpringAiDocumentIngestionPipeline springAiDocumentIngestionPipeline;

    public DefaultDocumentIngestionService(
            DocumentImportSourceFactory documentImportSourceFactory,
            SpringAiDocumentIngestionPipeline springAiDocumentIngestionPipeline
    ) {
        this.documentImportSourceFactory = documentImportSourceFactory;
        this.springAiDocumentIngestionPipeline = springAiDocumentIngestionPipeline;
    }

    /** 导入一篇纯文本文档，并返回导入后的文档信息。 */
    @Override
    @Transactional
    public DocumentImportResponse importText(DocumentImportRequest request) {
        DocumentImportSource source = documentImportSourceFactory.fromTextRequest(request);
        return springAiDocumentIngestionPipeline.ingestText(source, request.content().trim());
    }

    /** 导入上传文件，并交由对应 reader 转换为统一文档。 */
    @Override
    @Transactional
    public DocumentImportResponse importFile(DocumentFileImportRequest request) {
        DocumentImportSource source = documentImportSourceFactory.fromFileRequest(request);
        return springAiDocumentIngestionPipeline.ingestFile(source, request);
    }
}
