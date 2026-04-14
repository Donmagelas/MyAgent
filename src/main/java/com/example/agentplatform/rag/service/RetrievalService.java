package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagProperties;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索编排服务。
 * 负责复用 Spring AI 的预检索增强链和文档合并逻辑，输出统一的检索结果列表。
 */
@Service
public class RetrievalService {

    public static final String WORKFLOW_ID_CONTEXT_KEY = "workflowId";

    private final SpringAiRagAdvisorFactory springAiRagAdvisorFactory;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;
    private final RagProperties ragProperties;

    public RetrievalService(
            SpringAiRagAdvisorFactory springAiRagAdvisorFactory,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper,
            RagProperties ragProperties
    ) {
        this.springAiRagAdvisorFactory = springAiRagAdvisorFactory;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
        this.ragProperties = ragProperties;
    }

    /**
     * 执行检索并返回项目内部的 chunk 结果。
     */
    public List<RetrievedChunk> retrieve(String query) {
        return retrieve(query, null);
    }

    public List<RetrievedChunk> retrieve(String query, Long workflowId) {
        List<Document> documents = springAiRagAdvisorFactory.retrieveDocuments(query, workflowId);
        return springAiRetrievedDocumentMapper.toRetrievedChunks(documents).stream()
                .limit(ragProperties.maxContextChunks())
                .toList();
    }
}
