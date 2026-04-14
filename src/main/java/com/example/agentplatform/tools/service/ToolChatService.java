package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.service.ChatPersistenceService;
import com.example.agentplatform.chat.service.ChatUsageService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.router.SkillRouterService;
import com.example.agentplatform.skills.service.SkillPromptBuilder;
import com.example.agentplatform.skills.service.SkillToolSelector;
import com.example.agentplatform.tools.client.DashScopeCompatibleToolChatClient;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import com.example.agentplatform.tools.domain.ToolResolverRequest;
import com.example.agentplatform.tools.dto.ToolChatRequest;
import com.example.agentplatform.tools.dto.ToolChatResponse;
import com.example.agentplatform.tools.dto.ToolInvocationRecord;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具对话服务。
 * 使用 Spring AI 的 ToolCallingManager 管理工具调用过程，并通过百炼兼容接口完成模型交互。
 */
@Service
public class ToolChatService {

    private final AiModelProperties aiModelProperties;
    private final ToolProperties toolProperties;
    private final ToolPromptService toolPromptService;
    private final ToolRegistryService toolRegistryService;
    private final ToolResolverService toolResolverService;
    private final ToolPermissionContextFactory toolPermissionContextFactory;
    private final SkillRouterService skillRouterService;
    private final SkillPromptBuilder skillPromptBuilder;
    private final SkillToolSelector skillToolSelector;
    private final ToolCallingManager toolCallingManager;
    private final DashScopeCompatibleToolChatClient toolChatClient;
    private final ChatPersistenceService chatPersistenceService;
    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final ChatUsageService chatUsageService;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final ToolExecutionWorkflowService toolExecutionWorkflowService;

    public ToolChatService(
            AiModelProperties aiModelProperties,
            ToolProperties toolProperties,
            ToolPromptService toolPromptService,
            ToolRegistryService toolRegistryService,
            ToolResolverService toolResolverService,
            ToolPermissionContextFactory toolPermissionContextFactory,
            SkillRouterService skillRouterService,
            SkillPromptBuilder skillPromptBuilder,
            SkillToolSelector skillToolSelector,
            ToolCallingManager toolCallingManager,
            DashScopeCompatibleToolChatClient toolChatClient,
            ChatPersistenceService chatPersistenceService,
            AuthenticatedUserAccessor authenticatedUserAccessor,
            ChatUsageService chatUsageService,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            ToolExecutionWorkflowService toolExecutionWorkflowService
    ) {
        this.aiModelProperties = aiModelProperties;
        this.toolProperties = toolProperties;
        this.toolPromptService = toolPromptService;
        this.toolRegistryService = toolRegistryService;
        this.toolResolverService = toolResolverService;
        this.toolPermissionContextFactory = toolPermissionContextFactory;
        this.skillRouterService = skillRouterService;
        this.skillPromptBuilder = skillPromptBuilder;
        this.skillToolSelector = skillToolSelector;
        this.toolCallingManager = toolCallingManager;
        this.toolChatClient = toolChatClient;
        this.chatPersistenceService = chatPersistenceService;
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.chatUsageService = chatUsageService;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.toolExecutionWorkflowService = toolExecutionWorkflowService;
    }

    /**
     * 执行一次工具对话。
     */
    @Transactional
    public ToolChatResponse chat(ToolChatRequest request, Authentication authentication) {
        Long userId = authenticatedUserAccessor.requireUserId(authentication);
        PermissionContext permissionContext = toolPermissionContextFactory.create(authentication);

        ChatAskRequest chatAskRequest = new ChatAskRequest(request.sessionId(), request.message(), null, null);
        Conversation conversation = chatPersistenceService.getOrCreateConversation(userId, chatAskRequest);
        ChatMessage userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());

        ToolExecutionContext toolExecutionContext = resolveToolExecutionContext(
                request,
                permissionContext,
                conversation
        );
        ToolExecutionWorkflowService.ExecutionWorkflow executionWorkflow = toolExecutionWorkflowService.start(
                userId,
                conversation,
                request.message(),
                toolExecutionContext.resolvedSkill(),
                toolExecutionContext.selectedTools()
        );
        List<Message> conversationHistory = new ArrayList<>();
        conversationHistory.add(new SystemMessage(toolExecutionContext.systemPrompt()));
        conversationHistory.add(new UserMessage(request.message()));

        List<ToolInvocationRecord> toolInvocations = new ArrayList<>();
        Instant startedAt = Instant.now();
        try {
            for (int round = 1; round <= toolProperties.maxIterations(); round++) {
                ToolLoopState toolLoopState = callModel(
                        conversationHistory,
                        permissionContext,
                        conversation,
                        toolExecutionContext.candidateToolCallbacks()
                );
                recordUsage(conversation.id(), userMessage.id(), toolLoopState.chatResponse(), round);

                AssistantMessage assistantMessage = toolLoopState.chatResponse().getResult().getOutput();
                if (!assistantMessage.hasToolCalls()) {
                    String answer = assistantMessage.getText() == null ? "" : assistantMessage.getText();
                    ChatMessage assistantRecord = chatPersistenceService.saveAssistantMessage(
                            userId,
                            conversation.id(),
                            answer,
                            resolveModelName(toolLoopState.chatResponse())
                    );
                    recordFinalUsage(conversation.id(), assistantRecord.id(), toolLoopState.chatResponse(), startedAt, "tool-chat-final");
                    toolExecutionWorkflowService.completeSuccess(userId, executionWorkflow, answer, null);
                    return new ToolChatResponse(
                            executionWorkflow.workflowId(),
                            conversation.id(),
                            conversation.sessionId(),
                            answer,
                            false,
                            null,
                            null,
                            List.copyOf(toolInvocations)
                    );
                }

                List<ToolInvocationRecord> currentInvocations = assistantMessage.getToolCalls().stream()
                        .map(toolCall -> new ToolInvocationRecord(
                                toolCall.id(),
                                toolCall.name(),
                                toolRegistryService.requireDefinition(toolCall.name()).returnDirect()
                        ))
                        .toList();
                toolInvocations.addAll(currentInvocations);
                currentInvocations.forEach(invocation -> toolExecutionWorkflowService.recordToolInvocation(
                        userId,
                        executionWorkflow,
                        invocation.toolCallId(),
                        invocation.toolName(),
                        invocation.returnDirect()
                ));

                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(
                        toolLoopState.prompt(),
                        toolLoopState.chatResponse()
                );
                throwIfPermissionDenied(toolExecutionResult);
                conversationHistory = new ArrayList<>(toolExecutionResult.conversationHistory());

                if (toolExecutionResult.returnDirect()) {
                    DirectToolResult directToolResult = extractDirectToolResult(toolExecutionResult);
                    ChatMessage assistantRecord = chatPersistenceService.saveAssistantMessage(
                            userId,
                            conversation.id(),
                            directToolResult.payload(),
                            resolveModelName(toolLoopState.chatResponse())
                    );
                    recordFinalUsage(conversation.id(), assistantRecord.id(), toolLoopState.chatResponse(), startedAt, "tool-chat-direct");
                    toolExecutionWorkflowService.completeSuccess(
                            userId,
                            executionWorkflow,
                            directToolResult.payload(),
                            directToolResult.toolName()
                    );
                    return new ToolChatResponse(
                            executionWorkflow.workflowId(),
                            conversation.id(),
                            conversation.sessionId(),
                            directToolResult.payload(),
                            true,
                            directToolResult.toolName(),
                            directToolResult.payload(),
                            List.copyOf(toolInvocations)
                    );
                }
            }
        }
        catch (Exception exception) {
            toolExecutionWorkflowService.completeFailure(userId, executionWorkflow, exception);
            throw exception;
        }

        ApplicationException exception = new ApplicationException("Tool chat exceeded the maximum number of iterations");
        toolExecutionWorkflowService.completeFailure(userId, executionWorkflow, exception);
        throw exception;
    }

    private ToolLoopState callModel(
            List<Message> conversationHistory,
            PermissionContext permissionContext,
            Conversation conversation,
            List<ToolCallback> candidateToolCallbacks
    ) {
        ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
                .model(aiModelProperties.chatModel())
                .temperature(aiModelProperties.chatTemperature())
                .toolCallbacks(candidateToolCallbacks)
                .toolContext(buildToolContext(permissionContext, conversation))
                .internalToolExecutionEnabled(true)
                .build();
        org.springframework.ai.chat.prompt.Prompt prompt = new org.springframework.ai.chat.prompt.Prompt(conversationHistory, options);
        List<ToolDefinition> toolDefinitions = toolCallingManager.resolveToolDefinitions(options);
        ChatResponse chatResponse = toolChatClient.call(prompt, toolDefinitions);
        return new ToolLoopState(prompt, chatResponse);
    }

    private Map<String, Object> buildToolContext(PermissionContext permissionContext, Conversation conversation) {
        return Map.of(
                ToolContextKeys.PERMISSION_CONTEXT, permissionContext,
                ToolContextKeys.USER_ID, permissionContext.userId(),
                ToolContextKeys.USERNAME, permissionContext.username(),
                ToolContextKeys.CONVERSATION_ID, conversation.id(),
                ToolContextKeys.SESSION_ID, conversation.sessionId()
        );
    }

    private DirectToolResult extractDirectToolResult(ToolExecutionResult toolExecutionResult) {
        List<Message> conversationHistory = toolExecutionResult.conversationHistory();
        for (int index = conversationHistory.size() - 1; index >= 0; index--) {
            Message message = conversationHistory.get(index);
            if (message instanceof ToolResponseMessage toolResponseMessage && !toolResponseMessage.getResponses().isEmpty()) {
                ToolResponseMessage.ToolResponse response = toolResponseMessage.getResponses().get(0);
                return new DirectToolResult(response.name(), response.responseData());
            }
        }
        throw new ApplicationException("Direct tool result is missing");
    }

    private ToolExecutionContext resolveToolExecutionContext(
            ToolChatRequest request,
            PermissionContext permissionContext,
            Conversation conversation
    ) {
        ResolvedSkill resolvedSkill = skillRouterService.route(request.message()).orElse(null);
        ToolResolverRequest resolverRequest = new ToolResolverRequest(
                permissionContext.userId(),
                conversation.id(),
                request.message(),
                permissionContext.roles(),
                0
        );
        List<RegisteredTool> resolvedTools = toolResolverService.resolve(resolverRequest);
        List<RegisteredTool> selectedTools = skillToolSelector.select(resolvedTools, resolvedSkill);
        return new ToolExecutionContext(
                skillPromptBuilder.build(toolPromptService.buildSystemPrompt(), resolvedSkill),
                selectedTools.stream().map(RegisteredTool::callback).toList(),
                resolvedSkill,
                selectedTools
        );
    }

    private record ToolExecutionContext(
            String systemPrompt,
            List<ToolCallback> candidateToolCallbacks,
            ResolvedSkill resolvedSkill,
            List<RegisteredTool> selectedTools
    ) {
    }

    private void throwIfPermissionDenied(ToolExecutionResult toolExecutionResult) {
        for (int index = toolExecutionResult.conversationHistory().size() - 1; index >= 0; index--) {
            Message message = toolExecutionResult.conversationHistory().get(index);
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                String responseData = response.responseData();
                if (responseData != null
                        && responseData.startsWith(ToolExecutionExceptionTextProcessor.PERMISSION_DENIED_PREFIX)) {
                    throw new AccessDeniedException(responseData.substring(
                            ToolExecutionExceptionTextProcessor.PERMISSION_DENIED_PREFIX.length()
                    ));
                }
            }
            return;
        }
    }

    private void recordUsage(Long conversationId, Long messageId, ChatResponse chatResponse, int round) {
        chatUsageService.save(
                conversationId,
                messageId,
                "tool-chat-model-round-" + round,
                springAiChatResponseMapper.toResult(chatResponse),
                0L,
                true,
                null
        );
    }

    private void recordFinalUsage(
            Long conversationId,
            Long messageId,
            ChatResponse chatResponse,
            Instant startedAt,
            String stepName
    ) {
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        chatUsageService.save(
                conversationId,
                messageId,
                stepName,
                springAiChatResponseMapper.toResult(chatResponse),
                latencyMs,
                true,
                null
        );
    }

    private String resolveModelName(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return aiModelProperties.chatModel();
        }
        String modelName = chatResponse.getMetadata().getModel();
        return modelName == null || modelName.isBlank() ? aiModelProperties.chatModel() : modelName;
    }

    private record ToolLoopState(
            org.springframework.ai.chat.prompt.Prompt prompt,
            ChatResponse chatResponse
    ) {
    }

    private record DirectToolResult(
            String toolName,
            String payload
    ) {
    }
}
