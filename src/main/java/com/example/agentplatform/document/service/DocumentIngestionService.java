package com.example.agentplatform.document.service;

import com.example.agentplatform.document.dto.DocumentImportRequest;
import com.example.agentplatform.document.dto.DocumentImportResponse;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;

/**
 * 知识导入能力的统一入口。
 */
public interface DocumentIngestionService {

    /** 导入一篇文本知识文档。 */
    DocumentImportResponse importText(DocumentImportRequest request);

    /** 导入一个支持类型的文件知识文档。 */
    DocumentImportResponse importFile(DocumentFileImportRequest request);
}
