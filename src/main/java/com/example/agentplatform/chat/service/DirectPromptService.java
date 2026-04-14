package com.example.agentplatform.chat.service;

import com.example.agentplatform.memory.domain.MemoryContext;
import org.springframework.stereotype.Service;

/**
 * 纯对话提示词构建服务。
 * 用于集中维护 direct chat 的系统提示词。
 */
@Service
public class DirectPromptService {

    /** 构建 direct chat 使用的基础系统提示词。 */
    public String buildSystemPrompt(MemoryContext memoryContext) {
        StringBuilder builder = new StringBuilder("""
                You are a helpful enterprise AI assistant.
                Answer the user directly and clearly.
                If the question depends on project-specific or document-specific facts that you do not have,
                say that you need the relevant documents or more context.
                """);
        if (memoryContext != null && memoryContext.renderedContext() != null && !memoryContext.renderedContext().isBlank()) {
            builder.append("\nMemory Context:\n")
                    .append(memoryContext.renderedContext())
                    .append('\n');
        }
        return builder.toString();
    }
}
