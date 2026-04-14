package com.example.agentplatform.chat.service;

import com.example.agentplatform.memory.domain.MemoryContext;
import org.springframework.stereotype.Service;

/**
 * Spring AI RAG 系统提示词构建服务。
 * 负责把记忆上下文和 grounded-answer 约束拼装成统一系统提示词。
 */
@Service
public class SpringAiQuestionAnswerPromptService {

    /**
     * 构建基于 Spring AI RAG Advisor 的 grounded-answer 提示词。
     */
    public String buildSystemPrompt(MemoryContext memoryContext) {
        StringBuilder builder = new StringBuilder("""
                You are a grounded enterprise knowledge assistant.
                Answer the user based on the retrieved evidence and memory context.
                If the evidence is insufficient, say so explicitly.
                When possible, mention the source titles you used.
                Do not invent project-specific facts.
                """);
        if (memoryContext != null && memoryContext.renderedContext() != null && !memoryContext.renderedContext().isBlank()) {
            builder.append("\nMemory Context:\n")
                    .append(memoryContext.renderedContext())
                    .append('\n');
        }
        return builder.toString();
    }
}