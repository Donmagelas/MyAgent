package com.example.agentplatform.agent.service;

import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.rag.service.RetrievalService;
import com.example.agentplatform.rag.service.SpringAiRetrievedDocumentMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Agent 内部的 RAG 动作服务。
 * 负责把一次检索动作转换为 observation 文本和可返回给前端的来源信息。
 */
@Service
public class AgentRagActionService {

    private final RetrievalService retrievalService;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;

    public AgentRagActionService(
            RetrievalService retrievalService,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper
    ) {
        this.retrievalService = retrievalService;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
    }

    /**
     * 执行一次检索动作，并把结果整理成统一的 observation 结构。
     */
    public AgentRagActionResult retrieve(String query) {
        return retrieve(query, null);
    }

    /**
     * 执行一次检索动作，并把当前 workflowId 透传到检索链路。
     */
    public AgentRagActionResult retrieve(String query, Long workflowId) {
        List<RetrievedChunk> chunks = retrievalService.retrieve(query, workflowId);
        List<ChatAskResponse.SourceItem> sources = springAiRetrievedDocumentMapper.toSourceItems(chunks);
        return new AgentRagActionResult(
                query,
                chunks,
                sources,
                buildObservation(query, chunks)
        );
    }

    private String buildObservation(String query, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "RAG 检索未找到相关证据。query=" + query;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("RAG 检索完成。query=").append(query)
                .append("，命中 ").append(chunks.size()).append(" 条证据。");
        chunks.stream()
                .limit(3)
                .forEach(chunk -> builder.append("\n- ")
                        .append(resolveTitle(chunk))
                        .append(" | ")
                        .append(resolveLocation(chunk))
                        .append(" | ")
                        .append(chunk.retrievalType())
                        .append(" | score=")
                        .append(String.format(Locale.ROOT, "%.4f", chunk.score())));
        return builder.toString();
    }

    private String resolveTitle(RetrievedChunk chunk) {
        if (chunk.chunkTitle() != null && !chunk.chunkTitle().isBlank()) {
            return chunk.chunkTitle();
        }
        if (chunk.documentTitle() != null && !chunk.documentTitle().isBlank()) {
            return chunk.documentTitle();
        }
        return "chunk-" + chunk.chunkIndex();
    }

    private String resolveLocation(RetrievedChunk chunk) {
        if (chunk.sectionPath() != null && !chunk.sectionPath().isBlank()) {
            return chunk.sectionPath();
        }
        if (chunk.jsonPath() != null && !chunk.jsonPath().isBlank()) {
            return chunk.jsonPath();
        }
        return chunk.documentTitle() == null ? "unknown" : chunk.documentTitle();
    }

    /**
     * Agent 内部一次检索动作的统一结果。
     */
    public record AgentRagActionResult(
            String query,
            List<RetrievedChunk> chunks,
            List<ChatAskResponse.SourceItem> sources,
            String observation
    ) {
    }
}
