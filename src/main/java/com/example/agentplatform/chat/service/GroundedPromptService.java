package com.example.agentplatform.chat.service;

import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 构建 grounded answer 模式下的系统提示词。
 * 把检索证据序列化成适合拼接进 prompt 的上下文块。
 */
@Service
public class GroundedPromptService {

    /** 构建 grounded-answer 模式下的系统提示词。 */
    public String buildSystemPrompt(List<RetrievedChunk> chunks, MemoryContext memoryContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a grounded enterprise knowledge assistant.\n");
        builder.append("Answer only based on the provided context.\n");
        builder.append("If the evidence is insufficient, say so explicitly.\n");
        builder.append("When possible, mention the source titles you used.\n\n");
        if (memoryContext != null && memoryContext.renderedContext() != null && !memoryContext.renderedContext().isBlank()) {
            builder.append("Memory Context:\n")
                    .append(memoryContext.renderedContext())
                    .append("\n\n");
        }
        builder.append("Context:\n");
        for (RetrievedChunk chunk : chunks) {
            builder.append("[Source: ").append(chunk.documentTitle())
                    .append(", chunk=").append(chunk.chunkIndex())
                    .append(", type=").append(chunk.retrievalType())
                    .append("]\n")
                    .append(chunk.content())
                    .append("\n\n");
        }
        return builder.toString();
    }
}
