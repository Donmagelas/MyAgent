package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.tools.domain.RegisteredTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务规划服务。
 * 在执行多步 Agent 之前，先让模型输出结构化 TaskPlan。
 */
@Service
public class TaskPlanningService {

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final AgentProperties agentProperties;
    private final AgentPromptService agentPromptService;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;

    public TaskPlanningService(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            AgentProperties agentProperties,
            AgentPromptService agentPromptService,
            SpringAiChatResponseMapper springAiChatResponseMapper
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.agentProperties = agentProperties;
        this.agentPromptService = agentPromptService;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
    }

    /**
     * 生成结构化任务计划。
     */
    public StructuredResult<TaskPlan> plan(
            AgentReasoningMode mode,
            String message,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools
    ) {
        BeanOutputConverter<TaskPlan> outputConverter = new BeanOutputConverter<>(TaskPlan.class);
        ChatClientResponse response = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(agentProperties.planning().temperature())
                        .maxTokens(agentProperties.planning().maxTokens())
                        .build())
                .system(agentPromptService.buildTaskPlanningSystemPrompt(mode, memoryContext, availableTools)
                        + "\n\n"
                        + outputConverter.getFormat())
                .user(agentPromptService.buildTaskPlanningUserPrompt(message))
                .call()
                .chatClientResponse();
        String content = springAiChatResponseMapper.extractAnswer(response.chatResponse());
        return new StructuredResult<>(outputConverter.convert(content), response);
    }

    /**
     * 结构化规划结果及其底层聊天响应。
     */
    public record StructuredResult<T>(
            T body,
            ChatClientResponse response
    ) {
    }
}