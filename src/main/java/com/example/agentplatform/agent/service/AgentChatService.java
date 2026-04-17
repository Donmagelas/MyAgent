package com.example.agentplatform.agent.service;

import com.example.agentplatform.advisor.domain.AdvisorOperation;
import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.advisor.service.ChatAdvisorExecutor;
import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentCotResult;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.AgentStepPlan;
import com.example.agentplatform.agent.domain.AgentStreamingExecutionPlan;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.agent.dto.AgentChatResponse;
import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.service.ChatPersistenceService;
import com.example.agentplatform.chat.service.ChatUsageService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.advisor.MemoryContextAdvisor;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.rag.service.RagAnswerJudgeService;
import com.example.agentplatform.rag.service.RagEvidenceGuardService;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.router.SkillRouterService;
import com.example.agentplatform.skills.service.SkillToolSelector;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolResolverRequest;
import com.example.agentplatform.tools.service.ToolPermissionContextFactory;
import com.example.agentplatform.tools.service.ToolResolverService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 对话服务。
 * 统一承载 CoT、ReAct 和 Agent Loop，并在默认情况下把 RAG、工具和子任务决策都放进同一个 loop 中。
 */
@Service
public class AgentChatService {

    private final AgentProperties agentProperties;
    private final AiModelProperties aiModelProperties;
    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final ChatPersistenceService chatPersistenceService;
    private final MemoryContextAdvisor memoryContextAdvisor;
    private final AgentStepPlannerService agentStepPlannerService;
    private final TaskPlanningService taskPlanningService;
    private final AgentToolExecutorService agentToolExecutorService;
    private final AgentRagActionService agentRagActionService;
    private final AgentRagRoutingService agentRagRoutingService;
    private final AgentExecutionWorkflowService agentExecutionWorkflowService;
    private final ToolPermissionContextFactory toolPermissionContextFactory;
    private final ToolResolverService toolResolverService;
    private final SkillRouterService skillRouterService;
    private final SkillToolSelector skillToolSelector;
    private final ChatAdvisorExecutor chatAdvisorExecutor;
    private final ChatUsageService chatUsageService;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final RagEvidenceGuardService ragEvidenceGuardService;
    private final RagAnswerJudgeService ragAnswerJudgeService;

    public AgentChatService(
            AgentProperties agentProperties,
            AiModelProperties aiModelProperties,
            AuthenticatedUserAccessor authenticatedUserAccessor,
            ChatPersistenceService chatPersistenceService,
            MemoryContextAdvisor memoryContextAdvisor,
            AgentStepPlannerService agentStepPlannerService,
            TaskPlanningService taskPlanningService,
            AgentToolExecutorService agentToolExecutorService,
            AgentRagActionService agentRagActionService,
            AgentRagRoutingService agentRagRoutingService,
            AgentExecutionWorkflowService agentExecutionWorkflowService,
            ToolPermissionContextFactory toolPermissionContextFactory,
            ToolResolverService toolResolverService,
            SkillRouterService skillRouterService,
            SkillToolSelector skillToolSelector,
            ChatAdvisorExecutor chatAdvisorExecutor,
            ChatUsageService chatUsageService,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            RagEvidenceGuardService ragEvidenceGuardService,
            RagAnswerJudgeService ragAnswerJudgeService
    ) {
        this.agentProperties = agentProperties;
        this.aiModelProperties = aiModelProperties;
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.chatPersistenceService = chatPersistenceService;
        this.memoryContextAdvisor = memoryContextAdvisor;
        this.agentStepPlannerService = agentStepPlannerService;
        this.taskPlanningService = taskPlanningService;
        this.agentToolExecutorService = agentToolExecutorService;
        this.agentRagActionService = agentRagActionService;
        this.agentRagRoutingService = agentRagRoutingService;
        this.agentExecutionWorkflowService = agentExecutionWorkflowService;
        this.toolPermissionContextFactory = toolPermissionContextFactory;
        this.toolResolverService = toolResolverService;
        this.skillRouterService = skillRouterService;
        this.skillToolSelector = skillToolSelector;
        this.chatAdvisorExecutor = chatAdvisorExecutor;
        this.chatUsageService = chatUsageService;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.ragEvidenceGuardService = ragEvidenceGuardService;
        this.ragAnswerJudgeService = ragAnswerJudgeService;
    }

    /**
     * 执行一次 Agent 对话。
     */
    @Transactional
    public AgentChatResponse chat(AgentChatRequest request, Authentication authentication) {
        return execute(request, authentication, AgentExecutionListener.noop());
    }

    /**
     * 执行一次带监听器的 Agent 对话。
     */
    @Transactional
    public AgentChatResponse execute(
            AgentChatRequest request,
            Authentication authentication,
            AgentExecutionListener executionListener
    ) {
        if (!agentProperties.enabled()) {
            throw new ApplicationException("Agent is disabled");
        }
        Long userId = authenticatedUserAccessor.requireUserId(authentication);
        AgentReasoningMode mode = resolveMode(request);
        int maxSteps = resolveMaxSteps(request);
        Conversation conversation = chatPersistenceService.getOrCreateConversation(
                userId,
                new ChatAskRequest(request.sessionId(), request.message(), null, null)
        );
        ChatMessage userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());
        MemoryContext memoryContext = memoryContextAdvisor.buildContext(userId, conversation.id(), request.message());
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow = agentExecutionWorkflowService.start(
                userId,
                conversation,
                request.message(),
                mode
        );
        executionListener.onStart(mode, conversation.id(), conversation.sessionId(), executionWorkflow.workflowId());
        Instant startedAt = Instant.now();
        try {
            return switch (mode) {
                case COT -> handleCot(request, conversation, userMessage, memoryContext, executionWorkflow, startedAt, userId, executionListener);
                case REACT, LOOP -> handleLoop(request, authentication, conversation, userMessage, memoryContext, executionWorkflow, startedAt, userId, mode, maxSteps, executionListener);
            };
        }
        catch (Exception exception) {
            executionListener.onError(exception);
            agentExecutionWorkflowService.completeFailure(userId, executionWorkflow, 0, exception);
            throw exception;
        }
    }

    /**
     * 为流式最终回答准备统一 Agent 执行结果。
     * 该方法只负责跑完规划、循环和工具/RAG 决策，不在这里生成最终整段答案。
     */
    @Transactional
    public AgentStreamingExecutionPlan prepareStreamingExecution(
            AgentChatRequest request,
            Authentication authentication,
            AgentExecutionListener executionListener
    ) {
        if (!agentProperties.enabled()) {
            throw new ApplicationException("Agent is disabled");
        }
        Long userId = authenticatedUserAccessor.requireUserId(authentication);
        AgentReasoningMode mode = resolveMode(request);
        int maxSteps = resolveMaxSteps(request);
        Conversation conversation = chatPersistenceService.getOrCreateConversation(
                userId,
                new ChatAskRequest(request.sessionId(), request.message(), null, null)
        );
        ChatMessage userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());
        MemoryContext memoryContext = memoryContextAdvisor.buildContext(userId, conversation.id(), request.message());
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow = agentExecutionWorkflowService.start(
                userId,
                conversation,
                request.message(),
                mode
        );
        executionListener.onStart(mode, conversation.id(), conversation.sessionId(), executionWorkflow.workflowId());

        try {
            return switch (mode) {
                case COT -> prepareCotStreaming(
                        request,
                        conversation,
                        userMessage,
                        memoryContext,
                        executionWorkflow,
                        userId,
                        executionListener
                );
                case REACT, LOOP -> prepareLoopStreaming(
                        request,
                        authentication,
                        conversation,
                        userMessage,
                        memoryContext,
                        executionWorkflow,
                        userId,
                        mode,
                        maxSteps,
                        executionListener
                );
            };
        }
        catch (Exception exception) {
            executionListener.onError(exception);
            agentExecutionWorkflowService.completeFailure(userId, executionWorkflow, 0, exception);
            throw exception;
        }
    }

    private AgentStreamingExecutionPlan prepareCotStreaming(
            AgentChatRequest request,
            Conversation conversation,
            ChatMessage userMessage,
            MemoryContext memoryContext,
            AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow,
            Long userId,
            AgentExecutionListener executionListener
    ) {
        Instant startedAt = Instant.now();
        AgentStepPlannerService.StructuredResult<AgentCotResult> result = agentStepPlannerService.planCot(
                buildInternalPlannerMessage(request),
                memoryContext
        );
        recordUsage(
                executionWorkflow.workflowId(),
                conversation.id(),
                userMessage.id(),
                "agent-cot-plan",
                result,
                startedAt,
                executionListener
        );
        AgentCotResult body = result.body();
        if (body == null || body.finalAnswer() == null || body.finalAnswer().isBlank()) {
            throw new ApplicationException("CoT result is missing finalAnswer");
        }
        agentExecutionWorkflowService.recordPlanningStep(
                userId,
                executionWorkflow,
                1,
                body.reasoningSummary(),
                AgentActionType.FINAL,
                null,
                body.finalAnswer()
        );
        executionListener.onPlanning(1, body.reasoningSummary(), AgentActionType.FINAL, null);
        return new AgentStreamingExecutionPlan(
                userId,
                executionWorkflow,
                conversation,
                memoryContext,
                AgentReasoningMode.COT,
                request.message(),
                body.finalAnswer(),
                body.reasoningSummary(),
                1,
                List.of(),
                List.of(),
                List.of(),
                false,
                "agent-cot-final"
        );
    }

    private AgentStreamingExecutionPlan prepareLoopStreaming(
            AgentChatRequest request,
            Authentication authentication,
            Conversation conversation,
            ChatMessage userMessage,
            MemoryContext memoryContext,
            AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow,
            Long userId,
            AgentReasoningMode mode,
            int maxSteps,
            AgentExecutionListener executionListener
    ) {
        Instant startedAt = Instant.now();
        PermissionContext permissionContext = toolPermissionContextFactory.create(authentication);
        SkillExecutionContext skillExecutionContext = resolveSkillExecutionContext(
                request,
                permissionContext,
                conversation,
                executionListener
        );
        List<RegisteredTool> availableTools = skillExecutionContext.availableTools();
        TaskPlan taskPlan = buildTaskPlan(
                request,
                executionWorkflow.workflowId(),
                mode,
                buildInternalPlannerMessage(request),
                memoryContext,
                availableTools,
                skillExecutionContext.resolvedSkill(),
                conversation,
                userMessage,
                startedAt,
                executionListener
        );
        if (taskPlan != null) {
            agentExecutionWorkflowService.recordTaskPlan(userId, executionWorkflow, taskPlan);
            executionListener.onTaskPlan(taskPlan);
        }

        List<Map<String, Object>> scratchpad = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();
        Map<String, ChatAskResponse.SourceItem> sourceItems = new LinkedHashMap<>();
        Map<String, RetrievedChunk> retrievedChunks = new LinkedHashMap<>();
        int executedSteps = 0;

        for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
            AgentStepPlannerService.StructuredResult<AgentStepPlan> result = agentStepPlannerService.planNextStep(
                    mode,
                    buildInternalPlannerMessage(request),
                    memoryContext,
                    availableTools,
                    scratchpad,
                    stepIndex,
                    maxSteps,
                    taskPlan,
                    skillExecutionContext.resolvedSkill()
            );
            recordUsage(
                    executionWorkflow.workflowId(),
                    conversation.id(),
                    userMessage.id(),
                    "agent-loop-plan-" + stepIndex,
                    result,
                    startedAt,
                    executionListener
            );

            AgentStepPlan stepPlan = result.body();
            executedSteps = stepIndex;
            stepPlan = applyKnowledgeRetrievalOverride(
                    request,
                    authentication,
                    stepPlan,
                    stepIndex,
                    sourceItems,
                    memoryContext,
                    executionWorkflow.workflowId(),
                    conversation.id(),
                    userMessage.id(),
                    startedAt,
                    executionListener
            );
            validateStepPlan(stepPlan);
            agentExecutionWorkflowService.recordPlanningStep(
                    userId,
                    executionWorkflow,
                    stepIndex,
                    stepPlan.thought(),
                    stepPlan.actionType(),
                    stepPlan.toolName(),
                    stepPlan.finalAnswer()
            );
            executionListener.onPlanning(
                    stepIndex,
                    stepPlan.thought(),
                    stepPlan.actionType(),
                    stepPlan.actionType() == AgentActionType.TOOL ? stepPlan.toolName() : null
            );

            if (stepPlan.actionType() == AgentActionType.FINAL) {
                String finalAnswer = stepPlan.finalAnswer();
                if (finalAnswer == null || finalAnswer.isBlank()) {
                    throw new ApplicationException("Agent final step is missing finalAnswer");
                }
                return new AgentStreamingExecutionPlan(
                        userId,
                        executionWorkflow,
                        conversation,
                        memoryContext,
                        mode,
                        request.message(),
                        finalAnswer,
                        buildLoopReasoningSummary(executedSteps, usedTools, sourceItems.values()),
                        executedSteps,
                        new ArrayList<>(usedTools),
                        new ArrayList<>(sourceItems.values()),
                        new ArrayList<>(retrievedChunks.values()),
                        false,
                        "agent-loop-final"
                );
            }

            if (stepPlan.actionType() == AgentActionType.RAG) {
                String ragQuery = resolveRagQuery(stepPlan, request.message());
                assertKnowledgeRetrieveAllowed(authentication, ragQuery);
                AgentRagActionService.AgentRagActionResult ragResult =
                        agentRagActionService.retrieve(ragQuery, executionWorkflow.workflowId());
                mergeSourceItems(sourceItems, ragResult.sources());
                mergeRetrievedChunks(retrievedChunks, ragResult.chunks());
                agentExecutionWorkflowService.recordRetrieval(
                        userId,
                        executionWorkflow,
                        stepIndex,
                        ragQuery,
                        ragResult.sources().size(),
                        ragResult.observation()
                );
                executionListener.onRetrieval(stepIndex, ragQuery, ragResult.sources());
                executionListener.onObservation(stepIndex, "rag", ragResult.observation());
                scratchpad.add(buildScratchpadEntry(
                        stepIndex,
                        stepPlan.thought(),
                        "RAG:" + ragQuery,
                        ragResult.observation()
                ));
                continue;
            }

            String observation;
            if (!agentToolExecutorService.isAvailableTool(stepPlan.toolName(), availableTools)) {
                observation = "Tool is unavailable in the current context: " + stepPlan.toolName();
                agentExecutionWorkflowService.recordObservation(userId, executionWorkflow, stepIndex, observation);
                executionListener.onObservation(stepIndex, stepPlan.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(
                        stepIndex,
                        stepPlan.thought(),
                        "UNAVAILABLE:" + stepPlan.toolName(),
                        observation
                ));
                continue;
            }

            try {
                AgentToolExecutorService.ToolExecutionOutcome toolResult = agentToolExecutorService.execute(
                        stepPlan.toolName(),
                        stepPlan.toolInput(),
                        permissionContext,
                        conversation,
                        executionWorkflow.workflowId(),
                        executionWorkflow.rootTaskId(),
                        stepIndex
                );
                usedTools.add(toolResult.toolName());
                observation = toolResult.observation();
                agentExecutionWorkflowService.recordToolExecution(
                        userId,
                        executionWorkflow,
                        stepIndex,
                        toolResult.toolName(),
                        stepPlan.toolInput(),
                        observation,
                        toolResult.returnDirect()
                );
                executionListener.onObservation(stepIndex, toolResult.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), toolResult.toolName(), observation));

                if (toolResult.returnDirect()) {
                    return new AgentStreamingExecutionPlan(
                            userId,
                            executionWorkflow,
                            conversation,
                            memoryContext,
                            mode,
                            request.message(),
                            observation,
                            buildLoopReasoningSummary(executedSteps, usedTools, sourceItems.values()),
                            executedSteps,
                            new ArrayList<>(usedTools),
                            new ArrayList<>(sourceItems.values()),
                            new ArrayList<>(retrievedChunks.values()),
                            true,
                            "agent-loop-direct-return"
                    );
                }
            }
            catch (Exception exception) {
                observation = "Tool execution failed: " + exception.getMessage();
                agentExecutionWorkflowService.recordObservation(userId, executionWorkflow, stepIndex, observation);
                executionListener.onObservation(stepIndex, stepPlan.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), stepPlan.toolName(), observation));
            }
        }

        throw new ApplicationException("Agent reached the maximum number of steps");
    }

    private AgentChatResponse handleCot(
            AgentChatRequest request,
            Conversation conversation,
            ChatMessage userMessage,
            MemoryContext memoryContext,
            AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow,
            Instant startedAt,
            Long userId,
            AgentExecutionListener executionListener
    ) {
        AgentStepPlannerService.StructuredResult<AgentCotResult> result = agentStepPlannerService.planCot(
                buildInternalPlannerMessage(request),
                memoryContext
        );
        recordUsage(
                executionWorkflow.workflowId(),
                conversation.id(),
                userMessage.id(),
                "agent-cot-plan",
                result,
                startedAt,
                executionListener
        );
        AgentCotResult body = result.body();
        if (body == null || body.finalAnswer() == null || body.finalAnswer().isBlank()) {
            throw new ApplicationException("CoT result is missing finalAnswer");
        }
        agentExecutionWorkflowService.recordPlanningStep(
                userId,
                executionWorkflow,
                1,
                body.reasoningSummary(),
                AgentActionType.FINAL,
                null,
                body.finalAnswer()
        );
        executionListener.onPlanning(1, body.reasoningSummary(), AgentActionType.FINAL, null);
        ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                userId,
                conversation.id(),
                body.finalAnswer(),
                resolveModelName(result)
        );
        recordUsage(
                executionWorkflow.workflowId(),
                conversation.id(),
                assistantMessage.id(),
                "agent-cot-final",
                result,
                startedAt,
                executionListener
        );
        agentExecutionWorkflowService.completeSuccess(
                userId,
                executionWorkflow,
                body.finalAnswer(),
                body.reasoningSummary(),
                1,
                List.of()
        );
        AgentChatResponse response = new AgentChatResponse(
                executionWorkflow.workflowId(),
                conversation.id(),
                conversation.sessionId(),
                AgentReasoningMode.COT,
                body.finalAnswer(),
                body.reasoningSummary(),
                1,
                List.of(),
                List.of()
        );
        executionListener.onFinal(response);
        return response;
    }

    private AgentChatResponse handleLoop(
            AgentChatRequest request,
            Authentication authentication,
            Conversation conversation,
            ChatMessage userMessage,
            MemoryContext memoryContext,
            AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow,
            Instant startedAt,
            Long userId,
            AgentReasoningMode mode,
            int maxSteps,
            AgentExecutionListener executionListener
    ) {
        PermissionContext permissionContext = toolPermissionContextFactory.create(authentication);
        SkillExecutionContext skillExecutionContext = resolveSkillExecutionContext(
                request,
                permissionContext,
                conversation,
                executionListener
        );
        List<RegisteredTool> availableTools = skillExecutionContext.availableTools();
        TaskPlan taskPlan = buildTaskPlan(
                request,
                executionWorkflow.workflowId(),
                mode,
                buildInternalPlannerMessage(request),
                memoryContext,
                availableTools,
                skillExecutionContext.resolvedSkill(),
                conversation,
                userMessage,
                startedAt,
                executionListener
        );
        if (taskPlan != null) {
            agentExecutionWorkflowService.recordTaskPlan(userId, executionWorkflow, taskPlan);
            executionListener.onTaskPlan(taskPlan);
        }

        List<Map<String, Object>> scratchpad = new ArrayList<>();
        Set<String> usedTools = new LinkedHashSet<>();
        Map<String, ChatAskResponse.SourceItem> sourceItems = new LinkedHashMap<>();
        Map<String, RetrievedChunk> retrievedChunks = new LinkedHashMap<>();
        int executedSteps = 0;

        for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
            AgentStepPlannerService.StructuredResult<AgentStepPlan> result = agentStepPlannerService.planNextStep(
                    mode,
                    buildInternalPlannerMessage(request),
                    memoryContext,
                    availableTools,
                    scratchpad,
                    stepIndex,
                    maxSteps,
                    taskPlan,
                    skillExecutionContext.resolvedSkill()
            );
            recordUsage(
                    executionWorkflow.workflowId(),
                    conversation.id(),
                    userMessage.id(),
                    "agent-loop-plan-" + stepIndex,
                    result,
                    startedAt,
                    executionListener
            );

            AgentStepPlan stepPlan = result.body();
            executedSteps = stepIndex;
            stepPlan = applyKnowledgeRetrievalOverride(
                    request,
                    authentication,
                    stepPlan,
                    stepIndex,
                    sourceItems,
                    memoryContext,
                    executionWorkflow.workflowId(),
                    conversation.id(),
                    userMessage.id(),
                    startedAt,
                    executionListener
            );
            validateStepPlan(stepPlan);
            agentExecutionWorkflowService.recordPlanningStep(
                    userId,
                    executionWorkflow,
                    stepIndex,
                    stepPlan.thought(),
                    stepPlan.actionType(),
                    stepPlan.toolName(),
                    stepPlan.finalAnswer()
            );
            executionListener.onPlanning(
                    stepIndex,
                    stepPlan.thought(),
                    stepPlan.actionType(),
                    stepPlan.actionType() == AgentActionType.TOOL ? stepPlan.toolName() : null
            );

            if (stepPlan.actionType() == AgentActionType.FINAL) {
                String finalAnswer = stepPlan.finalAnswer();
                if (finalAnswer == null || finalAnswer.isBlank()) {
                    throw new ApplicationException("Agent final step is missing finalAnswer");
                }
                finalAnswer = applyRagGroundingSafeguard(
                        request.message(),
                        finalAnswer,
                        retrievedChunks,
                        executionWorkflow.workflowId(),
                        conversation.id(),
                        userMessage.id(),
                        startedAt,
                        executedSteps,
                        executionListener,
                        "agent-loop-rag-judge"
                );
                ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                        userId,
                        conversation.id(),
                        finalAnswer,
                        resolveModelName(result)
                );
                recordUsage(
                        executionWorkflow.workflowId(),
                        conversation.id(),
                        assistantMessage.id(),
                        "agent-loop-final",
                        result,
                        startedAt,
                        executionListener
                );
                String reasoningSummary = buildLoopReasoningSummary(executedSteps, usedTools, sourceItems.values());
                agentExecutionWorkflowService.completeSuccess(
                        userId,
                        executionWorkflow,
                        finalAnswer,
                        reasoningSummary,
                        executedSteps,
                        new ArrayList<>(usedTools)
                );
                AgentChatResponse response = new AgentChatResponse(
                        executionWorkflow.workflowId(),
                        conversation.id(),
                        conversation.sessionId(),
                        mode,
                        finalAnswer,
                        reasoningSummary,
                        executedSteps,
                        new ArrayList<>(usedTools),
                        new ArrayList<>(sourceItems.values())
                );
                executionListener.onFinal(response);
                return response;
            }

            if (stepPlan.actionType() == AgentActionType.RAG) {
                String ragQuery = resolveRagQuery(stepPlan, request.message());
                assertKnowledgeRetrieveAllowed(authentication, ragQuery);
                AgentRagActionService.AgentRagActionResult ragResult =
                        agentRagActionService.retrieve(ragQuery, executionWorkflow.workflowId());
                mergeSourceItems(sourceItems, ragResult.sources());
                mergeRetrievedChunks(retrievedChunks, ragResult.chunks());
                agentExecutionWorkflowService.recordRetrieval(
                        userId,
                        executionWorkflow,
                        stepIndex,
                        ragQuery,
                        ragResult.sources().size(),
                        ragResult.observation()
                );
                executionListener.onRetrieval(stepIndex, ragQuery, ragResult.sources());
                executionListener.onObservation(stepIndex, "rag", ragResult.observation());
                scratchpad.add(buildScratchpadEntry(
                        stepIndex,
                        stepPlan.thought(),
                        "RAG:" + ragQuery,
                        ragResult.observation()
                ));
                continue;
            }

            String observation;
            if (!agentToolExecutorService.isAvailableTool(stepPlan.toolName(), availableTools)) {
                observation = "Tool is unavailable in the current context: " + stepPlan.toolName();
                agentExecutionWorkflowService.recordObservation(userId, executionWorkflow, stepIndex, observation);
                executionListener.onObservation(stepIndex, stepPlan.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(
                        stepIndex,
                        stepPlan.thought(),
                        "UNAVAILABLE:" + stepPlan.toolName(),
                        observation
                ));
                continue;
            }

            try {
                AgentToolExecutorService.ToolExecutionOutcome toolResult = agentToolExecutorService.execute(
                        stepPlan.toolName(),
                        stepPlan.toolInput(),
                        permissionContext,
                        conversation,
                        executionWorkflow.workflowId(),
                        executionWorkflow.rootTaskId(),
                        stepIndex
                );
                usedTools.add(toolResult.toolName());
                observation = toolResult.observation();
                agentExecutionWorkflowService.recordToolExecution(
                        userId,
                        executionWorkflow,
                        stepIndex,
                        toolResult.toolName(),
                        stepPlan.toolInput(),
                        observation,
                        toolResult.returnDirect()
                );
                executionListener.onObservation(stepIndex, toolResult.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), toolResult.toolName(), observation));

                if (toolResult.returnDirect()) {
                    ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                            userId,
                            conversation.id(),
                            observation,
                            aiModelProperties.chatModel()
                    );
                    String reasoningSummary = buildLoopReasoningSummary(executedSteps, usedTools, sourceItems.values());
                    var usageRecord = chatUsageService.save(
                            executionWorkflow.workflowId(),
                            null,
                            conversation.id(),
                            assistantMessage.id(),
                            "agent-loop-direct-return",
                            springAiChatResponseMapper.toResult(
                                    null,
                                    aiModelProperties.chatModel(),
                                    observation,
                                    null,
                                    null,
                                    null
                            ),
                            Duration.between(startedAt, Instant.now()).toMillis(),
                            true,
                            null
                    );
                    executionListener.onUsage(usageRecord);
                    agentExecutionWorkflowService.completeSuccess(
                            userId,
                            executionWorkflow,
                            observation,
                            reasoningSummary,
                            executedSteps,
                            new ArrayList<>(usedTools)
                    );
                    AgentChatResponse response = new AgentChatResponse(
                            executionWorkflow.workflowId(),
                            conversation.id(),
                            conversation.sessionId(),
                            mode,
                            observation,
                            reasoningSummary,
                            executedSteps,
                            new ArrayList<>(usedTools),
                            new ArrayList<>(sourceItems.values())
                    );
                    executionListener.onFinal(response);
                    return response;
                }
            }
            catch (Exception exception) {
                observation = "Tool execution failed: " + exception.getMessage();
                agentExecutionWorkflowService.recordObservation(userId, executionWorkflow, stepIndex, observation);
                executionListener.onObservation(stepIndex, stepPlan.toolName(), observation);
                scratchpad.add(buildScratchpadEntry(stepIndex, stepPlan.thought(), stepPlan.toolName(), observation));
            }
        }

        throw new ApplicationException("Agent reached the maximum number of steps");
    }

    /**
     * 先按 query 路由 skill，再根据 skill 元数据暴露允许的工具。
     * 没有命中 skill 时保留原有 query 工具解析逻辑，避免影响普通对话。
     */
    private SkillExecutionContext resolveSkillExecutionContext(
            AgentChatRequest request,
            PermissionContext permissionContext,
            Conversation conversation,
            AgentExecutionListener executionListener
    ) {
        ToolResolverRequest resolverRequest = new ToolResolverRequest(
                permissionContext.userId(),
                conversation.id(),
                request.message(),
                permissionContext.roles(),
                0
        );
        ResolvedSkill resolvedSkill = skillRouterService.route(request.message()).orElse(null);
        if (resolvedSkill == null) {
            return new SkillExecutionContext(null, toolResolverService.resolve(resolverRequest));
        }
        List<RegisteredTool> visibleTools = toolResolverService.resolveVisibleTools(resolverRequest);
        List<RegisteredTool> availableTools = skillToolSelector.select(visibleTools, resolvedSkill);
        executionListener.onSkillSelected(resolvedSkill, availableTools);
        return new SkillExecutionContext(resolvedSkill, availableTools);
    }

    private TaskPlan buildTaskPlan(
            AgentChatRequest request,
            Long workflowId,
            AgentReasoningMode mode,
            String message,
            MemoryContext memoryContext,
            List<RegisteredTool> availableTools,
            ResolvedSkill resolvedSkill,
            Conversation conversation,
            ChatMessage userMessage,
            Instant startedAt,
            AgentExecutionListener executionListener
    ) {
        if (mode == AgentReasoningMode.COT
                || agentProperties.planning() == null
                || !agentProperties.planning().enabled()) {
            return null;
        }
        TaskPlanningService.StructuredResult<TaskPlan> result = taskPlanningService.plan(
                mode,
                message,
                memoryContext,
                availableTools,
                resolvedSkill
        );
        recordUsage(
                workflowId,
                conversation.id(),
                userMessage.id(),
                "agent-task-plan",
                result,
                startedAt,
                executionListener
        );
        return result.body();
    }

    private AgentReasoningMode resolveMode(AgentChatRequest request) {
        if (request.mode() != null) {
            return request.mode();
        }
        return agentProperties.defaultMode() == null ? AgentReasoningMode.LOOP : agentProperties.defaultMode();
    }

    private int resolveMaxSteps(AgentChatRequest request) {
        if (request.maxSteps() != null && request.maxSteps() > 0) {
            return request.maxSteps();
        }
        return Math.max(agentProperties.loop().maxIterations(), 1);
    }

    private void validateStepPlan(AgentStepPlan stepPlan) {
        if (stepPlan == null || stepPlan.actionType() == null) {
            throw new ApplicationException("Agent step plan is incomplete");
        }
        if (stepPlan.actionType() == AgentActionType.TOOL
                && (stepPlan.toolName() == null || stepPlan.toolName().isBlank())) {
            throw new ApplicationException("Tool step is missing toolName");
        }
        if (stepPlan.actionType() == AgentActionType.RAG
                && (stepPlan.ragQuery() == null || stepPlan.ragQuery().isBlank())) {
            throw new ApplicationException("RAG step is missing ragQuery");
        }
    }

    private Map<String, Object> buildScratchpadEntry(int stepIndex, String thought, String action, String observation) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", stepIndex);
        entry.put("thought", thought == null ? "" : thought);
        entry.put("action", action == null ? "" : action);
        entry.put("observation", observation == null ? "" : observation);
        return entry;
    }

    private String buildLoopReasoningSummary(
            int stepCount,
            Set<String> usedTools,
            Iterable<ChatAskResponse.SourceItem> sources
    ) {
        int sourceCount = 0;
        for (ChatAskResponse.SourceItem ignored : sources) {
            sourceCount++;
        }
        if (usedTools == null || usedTools.isEmpty()) {
            return "Completed " + stepCount + " steps, used no tools, sourceCount=" + sourceCount;
        }
        return "Completed " + stepCount + " steps, tools="
                + String.join(", ", usedTools)
                + ", sourceCount="
                + sourceCount;
    }

    private String resolveRagQuery(AgentStepPlan stepPlan, String fallbackQuery) {
        if (stepPlan.ragQuery() != null && !stepPlan.ragQuery().isBlank()) {
            return stepPlan.ragQuery();
        }
        return fallbackQuery;
    }

    /**
     * 在统一 Agent Loop 内部执行 RAG 前，显式复用知识检索 advisor。
     * 这样即使用户统一从 /api/chat/** 进入，也不能绕过 KNOWLEDGE_RETRIEVE 权限。
     */
    private void assertKnowledgeRetrieveAllowed(Authentication authentication, String ragQuery) {
        chatAdvisorExecutor.execute(new ChatAdvisorContext(
                AdvisorOperation.KNOWLEDGE_RETRIEVE,
                ragQuery,
                authentication
        ));
    }

    private AgentStepPlan applyKnowledgeRetrievalOverride(
            AgentChatRequest request,
            Authentication authentication,
            AgentStepPlan stepPlan,
            int stepIndex,
            Map<String, ChatAskResponse.SourceItem> sourceItems,
            MemoryContext memoryContext,
            Long workflowId,
            Long conversationId,
            Long messageId,
            Instant startedAt,
            AgentExecutionListener executionListener
    ) {
        if (stepPlan == null) {
            return null;
        }
        if (stepIndex != 1 || !sourceItems.isEmpty() || stepPlan.actionType() == AgentActionType.RAG) {
            return stepPlan;
        }
        if (!canBiasToKnowledgeRetrieval(authentication)) {
            return stepPlan;
        }
        if (!Boolean.TRUE.equals(request.preferKnowledgeRetrieval()) && !supportsKnowledgeBias(stepPlan)) {
            return stepPlan;
        }
        AgentRagRoutingService.RagRoutingDecision decision =
                agentRagRoutingService.decide(request, memoryContext, workflowId);
        recordRagRoutingUsageIfPresent(
                workflowId,
                conversationId,
                messageId,
                decision,
                startedAt,
                executionListener
        );
        executionListener.onRagRoutingDecision(decision);
        if (!decision.forceRag()) {
            return stepPlan;
        }
        return buildRagOverrideStep(
                stepPlan,
                decision.reason(),
                decision.retrievalQuery()
        );
    }

    /**
     * 仅在首步、无证据时，把 FINAL / 搜索工具决策收束成一次优先知识检索。
     */
    private AgentStepPlan buildRagOverrideStep(
            AgentStepPlan originalPlan,
            String reason,
            String ragQuery
    ) {
        String overrideThought = (originalPlan.thought() == null ? "" : originalPlan.thought() + " ")
                + reason;
        return new AgentStepPlan(
                overrideThought,
                AgentActionType.RAG,
                ragQuery,
                null,
                null,
                null
        );
    }

    /**
     * 只对 FINAL 或知识查询类工具动作做 RAG 倾向增强。
     */
    private boolean supportsKnowledgeBias(AgentStepPlan stepPlan) {
        if (stepPlan.actionType() == AgentActionType.FINAL) {
            return true;
        }
        if (stepPlan.actionType() != AgentActionType.TOOL) {
            return false;
        }
        return "search_web".equals(stepPlan.toolName()) || "fetch_webpage".equals(stepPlan.toolName());
    }

    /**
     * 只有具备知识检索权限的用户，才启用更激进的 RAG 倾向增强。
     */
    private boolean canBiasToKnowledgeRetrieval(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> SecurityRole.authority(SecurityRole.KNOWLEDGE_USER).equals(authority)
                        || SecurityRole.authority(SecurityRole.KNOWLEDGE_ADMIN).equals(authority));
    }

    /**
     * 当统一 Agent Loop 内部已经执行过 RAG 时，对最终答案做证据 gate 和回答后 judge。
     * 只有确实拿到检索证据时才会进入这条链，避免把普通直答错误地当成 grounded answer 处理。
     */
    private String buildInternalPlannerMessage(AgentChatRequest request) {
        if (Boolean.TRUE.equals(request.preferKnowledgeRetrieval())
                && request.knowledgeDocumentHint() != null
                && !request.knowledgeDocumentHint().isBlank()) {
            return """
                    当前会话刚上传了知识文档《%s》。
                    如果需要检索知识库，请优先基于这份文档进行检索和回答。
                    用户原始问题：%s
                    """.formatted(request.knowledgeDocumentHint(), request.message());
        }
        return request.message();
    }

    private void mergeSourceItems(
            Map<String, ChatAskResponse.SourceItem> sourceItems,
            List<ChatAskResponse.SourceItem> newSources
    ) {
        if (newSources == null || newSources.isEmpty()) {
            return;
        }
        for (ChatAskResponse.SourceItem source : newSources) {
            sourceItems.putIfAbsent(buildSourceKey(source), source);
        }
    }

    /**
     * 合并一次 RAG 动作返回的实际 chunk 结果。
     */
    private void mergeRetrievedChunks(
            Map<String, RetrievedChunk> retrievedChunks,
            List<RetrievedChunk> newChunks
    ) {
        if (newChunks == null || newChunks.isEmpty()) {
            return;
        }
        for (RetrievedChunk chunk : newChunks) {
            retrievedChunks.putIfAbsent(buildRetrievedChunkKey(chunk), chunk);
        }
    }

    private String buildSourceKey(ChatAskResponse.SourceItem source) {
        if (source.chunkId() != null) {
            return "chunk:" + source.chunkId();
        }
        return "document:" + source.documentId() + ":" + source.chunkIndex();
    }

    private String buildRetrievedChunkKey(RetrievedChunk chunk) {
        if (chunk.chunkId() != null) {
            return "chunk:" + chunk.chunkId();
        }
        return "document:" + chunk.documentId() + ":" + chunk.chunkIndex();
    }

    /**
     * 当统一 Agent Loop 内部已经执行过 RAG 时，对最终答案做证据 gate 和回答后 judge。
     */
    private String applyRagGroundingSafeguard(
            String question,
            String answer,
            Map<String, RetrievedChunk> retrievedChunks,
            Long workflowId,
            Long conversationId,
            Long messageId,
            Instant startedAt,
            int stepIndex,
            AgentExecutionListener executionListener,
            String judgeStepName
    ) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return answer;
        }
        List<RetrievedChunk> chunkList = new ArrayList<>(retrievedChunks.values());
        RagEvidenceAssessment evidenceAssessment = ragEvidenceGuardService.assess(question, chunkList);
        if (!evidenceAssessment.sufficient()) {
            executionListener.onEvidenceGate(stepIndex, evidenceAssessment.reason(), null);
            return ragEvidenceGuardService.buildInsufficientAnswer();
        }

        RagAnswerJudgeService.StructuredJudgeResult judgeResult =
                ragAnswerJudgeService.judge(question, answer, chunkList);
        if (judgeResult.response() != null) {
            var usageRecord = chatUsageService.save(
                    workflowId,
                    null,
                    conversationId,
                    messageId,
                    judgeStepName,
                    springAiChatResponseMapper.toResult(judgeResult.response().chatResponse()),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    true,
                    null
            );
            executionListener.onUsage(usageRecord);
        }
        if (judgeResult.body() != null && judgeResult.body().downgradeToInsufficient()) {
            executionListener.onJudgeDowngrade(stepIndex, judgeResult.body().reason(), judgeStepName);
            String safeAnswer = judgeResult.body().safeAnswer();
            return safeAnswer == null || safeAnswer.isBlank()
                    ? ragEvidenceGuardService.buildInsufficientAnswer()
                    : safeAnswer;
        }
        return answer;
    }

    private void recordUsage(
            Long workflowId,
            Long conversationId,
            Long messageId,
            String stepName,
            AgentStepPlannerService.StructuredResult<?> result,
            Instant startedAt,
            AgentExecutionListener executionListener
    ) {
        var usageRecord = chatUsageService.save(
                workflowId,
                null,
                conversationId,
                messageId,
                stepName,
                springAiChatResponseMapper.toResult(result.response().chatResponse()),
                Duration.between(startedAt, Instant.now()).toMillis(),
                true,
                null
        );
        executionListener.onUsage(usageRecord);
    }

    private void recordUsage(
            Long workflowId,
            Long conversationId,
            Long messageId,
            String stepName,
            TaskPlanningService.StructuredResult<?> result,
            Instant startedAt,
            AgentExecutionListener executionListener
    ) {
        var usageRecord = chatUsageService.save(
                workflowId,
                null,
                conversationId,
                messageId,
                stepName,
                springAiChatResponseMapper.toResult(result.response().chatResponse()),
                Duration.between(startedAt, Instant.now()).toMillis(),
                true,
                null
        );
        executionListener.onUsage(usageRecord);
    }

    /**
     * 如果 RAG 路由执行了 AI 分类器，则把分类器 token usage 计入当前工作流。
     */
    private void recordRagRoutingUsageIfPresent(
            Long workflowId,
            Long conversationId,
            Long messageId,
            AgentRagRoutingService.RagRoutingDecision decision,
            Instant startedAt,
            AgentExecutionListener executionListener
    ) {
        if (decision == null || decision.classifierResult() == null || decision.classifierResult().response() == null) {
            return;
        }
        var usageRecord = chatUsageService.save(
                workflowId,
                null,
                conversationId,
                messageId,
                "agent-rag-route-classifier",
                springAiChatResponseMapper.toResult(decision.classifierResult().response().chatResponse()),
                Duration.between(startedAt, Instant.now()).toMillis(),
                true,
                null
        );
        executionListener.onUsage(usageRecord);
    }

    /**
     * 主链路中 skill 路由后的执行上下文。
     */
    private record SkillExecutionContext(
            ResolvedSkill resolvedSkill,
            List<RegisteredTool> availableTools
    ) {
    }

    private String resolveModelName(AgentStepPlannerService.StructuredResult<?> result) {
        if (result.response().chatResponse() == null || result.response().chatResponse().getMetadata() == null) {
            return aiModelProperties.chatModel();
        }
        String modelName = result.response().chatResponse().getMetadata().getModel();
        return modelName == null || modelName.isBlank() ? aiModelProperties.chatModel() : modelName;
    }
}
