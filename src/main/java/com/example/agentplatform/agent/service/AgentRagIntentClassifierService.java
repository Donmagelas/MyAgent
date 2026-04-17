package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.RagIntentClassification;
import com.example.agentplatform.agent.domain.RagIntentDecision;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AgentRagRoutingProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * RAG 意图 AI 分类服务。
 * 只负责判断问题是否需要知识库，不直接执行检索或改写用户可见内容。
 */
@Service
public class AgentRagIntentClassifierService {

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final AgentRagRoutingProperties properties;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;

    public AgentRagIntentClassifierService(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            AgentRagRoutingProperties properties,
            SpringAiChatResponseMapper springAiChatResponseMapper
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.properties = properties;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
    }

    /**
     * 让模型输出结构化 RAG 路由判断。
     */
    public StructuredResult classify(
            String message,
            MemoryContext memoryContext,
            boolean hasKnowledgeDocumentHint,
            String knowledgeDocumentHint,
            AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision
    ) {
        BeanOutputConverter<RagIntentClassification> outputConverter =
                new BeanOutputConverter<>(RagIntentClassification.class);
        ChatClientResponse response = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(properties.classifierTemperature())
                        .maxTokens(properties.classifierMaxTokens())
                        .build())
                .system(buildSystemPrompt() + "\n\n" + outputConverter.getFormat())
                .user(buildUserPrompt(message, memoryContext, hasKnowledgeDocumentHint, knowledgeDocumentHint, heuristicDecision))
                .call()
                .chatClientResponse();
        String content = springAiChatResponseMapper.extractAnswer(response.chatResponse());
        RagIntentClassification body = normalize(outputConverter.convert(content), message, knowledgeDocumentHint);
        return new StructuredResult(body, response);
    }

    private String buildSystemPrompt() {
        return """
                You are a routing classifier for an enterprise RAG system.
                Decide whether the current user request should enter knowledge-base retrieval before answering.

                Decision meanings:
                - MUST_RAG: The answer depends on private/project/document knowledge or recently uploaded knowledge.
                - MAYBE_RAG: The request may depend on knowledge-base material, but a retrieval probe should verify evidence.
                - NO_RAG: The request is casual chat, pure rewriting/translation, generic advice, or can be answered without private knowledge.

                Rules:
                1. Prefer MUST_RAG for document-defined facts, product/gameplay rules, configuration, parameters, APIs, fields, versions, requirements, or project-specific behavior.
                2. Prefer MUST_RAG when the user explicitly asks to use uploaded documents, knowledge base, files, materials, or current document.
                3. Prefer MAYBE_RAG for ambiguous factual questions that mention a domain entity but do not clearly reference the knowledge base.
                4. Prefer NO_RAG for greetings, role-play, pure creative writing, translation, polishing, or generic public facts.
                5. retrievalQuery must be a concise search query, not an answer.
                6. needsWebSearch should be true only when public web freshness is more appropriate than private knowledge-base retrieval.
                7. Return structured output only.
                """;
    }

    private String buildUserPrompt(
            String message,
            MemoryContext memoryContext,
            boolean hasKnowledgeDocumentHint,
            String knowledgeDocumentHint,
            AgentRagRoutingHeuristicService.RagRoutingDecision heuristicDecision
    ) {
        return """
                User request:
                %s

                Recently uploaded knowledge document:
                %s

                Has uploaded knowledge document hint: %s

                Short-term memory count: %d
                Long-term memory count: %d
                Memory summary count: %d

                Rule heuristic:
                - forceRag: %s
                - reason: %s

                Classify the RAG intent.
                """.formatted(
                message == null ? "" : message,
                knowledgeDocumentHint == null || knowledgeDocumentHint.isBlank() ? "none" : knowledgeDocumentHint,
                hasKnowledgeDocumentHint,
                memoryContext == null || memoryContext.recentMessages() == null ? 0 : memoryContext.recentMessages().size(),
                memoryContext == null || memoryContext.stableFacts() == null ? 0 : memoryContext.stableFacts().size(),
                memoryContext == null || memoryContext.recalledSummaries() == null ? 0 : memoryContext.recalledSummaries().size(),
                heuristicDecision != null && heuristicDecision.forceRag(),
                heuristicDecision == null ? "" : heuristicDecision.reason()
        );
    }

    private RagIntentClassification normalize(
            RagIntentClassification classification,
            String originalMessage,
            String knowledgeDocumentHint
    ) {
        if (classification == null || classification.decision() == null) {
            return new RagIntentClassification(
                    RagIntentDecision.NO_RAG,
                    0.0d,
                    "Classifier returned an empty decision.",
                    originalMessage,
                    false
            );
        }
        String retrievalQuery = classification.retrievalQuery();
        if (retrievalQuery == null || retrievalQuery.isBlank()) {
            retrievalQuery = originalMessage;
        }
        if (knowledgeDocumentHint != null && !knowledgeDocumentHint.isBlank()
                && !retrievalQuery.contains(knowledgeDocumentHint)) {
            retrievalQuery = retrievalQuery + " " + knowledgeDocumentHint;
        }
        double confidence = Math.max(0.0d, Math.min(1.0d, classification.confidence()));
        return new RagIntentClassification(
                classification.decision(),
                confidence,
                classification.reason() == null ? "" : classification.reason(),
                retrievalQuery,
                classification.needsWebSearch()
        );
    }

    /**
     * 分类结果和底层模型响应，供调用方记录 token usage。
     */
    public record StructuredResult(
            RagIntentClassification body,
            ChatClientResponse response
    ) {
    }
}
