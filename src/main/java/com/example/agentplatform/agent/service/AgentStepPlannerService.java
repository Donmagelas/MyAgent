package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentStepPlan;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.tools.domain.RegisteredTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent 单步规划服务。
 * 负责把统一 Agent Loop 的每一步决策转换成结构化结果。
 */
@Service
public class AgentStepPlannerService {

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final AgentProperties agentProperties;
    private final AgentPromptService agentPromptService;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;

    public AgentStepPlannerService(
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
     * 执行一次统一 Agent Loop 规划。
     */
    public StructuredResult<AgentStepPlan> planNextStep(
            String message,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            List<Map<String, Object>> scratchpad,
            int stepIndex,
            int maxSteps
    ) {
        return planNextStep(message, memoryContext, availableTools, scratchpad, stepIndex, maxSteps, null, null);
    }

    /**
     * 执行一次统一 Agent Loop 规划，并允许传入任务计划。
     */
    public StructuredResult<AgentStepPlan> planNextStep(
            String message,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            List<Map<String, Object>> scratchpad,
            int stepIndex,
            int maxSteps,
            TaskPlan taskPlan
    ) {
        return planNextStep(message, memoryContext, availableTools, scratchpad, stepIndex, maxSteps, taskPlan, null);
    }

    /**
     * 执行带 skill 上下文的统一 Agent Loop 规划。
     */
    public StructuredResult<AgentStepPlan> planNextStep(
            String message,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            List<Map<String, Object>> scratchpad,
            int stepIndex,
            int maxSteps,
            TaskPlan taskPlan,
            ResolvedSkill resolvedSkill
    ) {
        BeanOutputConverter<AgentStepPlan> outputConverter = new BeanOutputConverter<>(AgentStepPlan.class);
        ChatClientResponse response = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(agentProperties.loop().plannerTemperature())
                        .maxTokens(agentProperties.loop().plannerMaxTokens())
                        .build())
                .system(agentPromptService.buildLoopPlannerSystemPrompt(
                                memoryContext,
                                availableTools,
                                taskPlan,
                                resolvedSkill
                        )
                        + "\n\n"
                        + outputConverter.getFormat())
                .user(agentPromptService.buildLoopPlannerUserPrompt(message, stepIndex, maxSteps, scratchpad))
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
