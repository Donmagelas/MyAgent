package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.MemoryStructuredQueryProperties;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring AI 的记忆查询意图解析器。
 * 用于把自然语言问题转换成结构化 MemoryQueryIntent，并补充 metadata 过滤条件。
 */
@Service
public class SpringAiMemoryQueryIntentParser implements MemoryQueryIntentParser {

    /**
     * 结构化解析 prompt，要求模型尽量保守，不要凭空猜测。
     */
    private static final String SYSTEM_PROMPT = """
            You are a memory query intent parser.
            Convert the user's question into a structured MemoryQueryIntent.

            Supported memoryTypes:
            1. USER_PREFERENCE: preferences, habits, response style, likes/dislikes.
            2. PROJECT_STATUS: project progress, milestones, current status.
            3. DESIGN_DECISION: design choices, architecture decisions, trade-offs.
            4. TASK_CONCLUSION: task results, conclusions, outcomes, completed work.
            5. STABLE_FACT: identity, name, role, location, stable facts.

            Also infer metadataFilter when the question explicitly asks about memory metadata:
            - autoExtracted = true when the question refers to automatically extracted memories.
            - triggerType = PERIODIC when the question refers to periodic or regular extraction.
            - triggerType = IMPORTANT_CONTENT when the question refers to important content extraction.
            - assistantMessageId when the question explicitly mentions assistantMessageId or a message id.

            Rules:
            - memoryTypes may contain multiple values if the question is broad.
            - subject should be short and specific only when the question clearly focuses on one topic.
            - minImportance should be set only when the user implies high-priority memories.
            - metadataFilter should be null when the question does not mention metadata constraints.
            - If nothing is certain, keep fields null or empty rather than guessing.
            """;

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final MemoryStructuredQueryProperties memoryStructuredQueryProperties;

    public SpringAiMemoryQueryIntentParser(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            MemoryStructuredQueryProperties memoryStructuredQueryProperties
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.memoryStructuredQueryProperties = memoryStructuredQueryProperties;
    }

    @Override
    public MemoryQueryIntent parse(String question) {
        BeanOutputConverter<MemoryQueryIntent> outputConverter = new BeanOutputConverter<>(MemoryQueryIntent.class);
        return chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(memoryStructuredQueryProperties.temperature())
                        .maxTokens(memoryStructuredQueryProperties.maxTokens())
                        .build())
                .system(SYSTEM_PROMPT)
                .user(question == null || question.isBlank()
                        ? "Please infer the memory query intent for an empty question."
                        : question)
                .call()
                .entity(outputConverter);
    }
}
