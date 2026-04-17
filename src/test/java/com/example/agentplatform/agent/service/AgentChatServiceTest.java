package com.example.agentplatform.agent.service;

import com.example.agentplatform.advisor.domain.AdvisorOperation;
import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.advisor.service.ChatAdvisorExecutor;
import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentCotResult;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.AgentStepPlan;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.domain.TaskPlanStep;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.agent.dto.AgentChatResponse;
import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.service.ChatCompletionClient;
import com.example.agentplatform.chat.service.ChatPersistenceService;
import com.example.agentplatform.chat.service.ChatUsageService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.advisor.MemoryContextAdvisor;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RagAnswerJudgeResult;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.service.RagAnswerJudgeService;
import com.example.agentplatform.rag.service.RagEvidenceGuardService;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.skills.router.SkillRouterService;
import com.example.agentplatform.skills.service.SkillToolSelector;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.RegisteredTool;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import com.example.agentplatform.tools.service.ToolPermissionContextFactory;
import com.example.agentplatform.tools.service.ToolResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

/**
 * Agent 对话服务测试。
 * 覆盖 CoT、统一 loop 内的 RAG 动作，以及工具直返路径。
 */
@ExtendWith(MockitoExtension.class)
class AgentChatServiceTest {

    @Mock
    private AuthenticatedUserAccessor authenticatedUserAccessor;
    @Mock
    private ChatPersistenceService chatPersistenceService;
    @Mock
    private MemoryContextAdvisor memoryContextAdvisor;
    @Mock
    private AgentStepPlannerService agentStepPlannerService;
    @Mock
    private TaskPlanningService taskPlanningService;
    @Mock
    private AgentToolExecutorService agentToolExecutorService;
    @Mock
    private AgentRagActionService agentRagActionService;
    @Mock
    private AgentRagRoutingService agentRagRoutingService;
    @Mock
    private AgentExecutionWorkflowService agentExecutionWorkflowService;
    @Mock
    private ToolPermissionContextFactory toolPermissionContextFactory;
    @Mock
    private ToolResolverService toolResolverService;
    @Mock
    private SkillRouterService skillRouterService;
    @Mock
    private SkillToolSelector skillToolSelector;
    @Mock
    private ChatAdvisorExecutor chatAdvisorExecutor;
    @Mock
    private ChatUsageService chatUsageService;
    @Mock
    private SpringAiChatResponseMapper springAiChatResponseMapper;
    @Mock
    private RagEvidenceGuardService ragEvidenceGuardService;
    @Mock
    private RagAnswerJudgeService ragAnswerJudgeService;
    @Mock
    private Authentication authentication;
    @Mock
    private ToolCallback toolCallback;

    private AgentChatService agentChatService;
    private AgentProperties agentProperties;
    private AiModelProperties aiModelProperties;
    private Conversation conversation;
    private ChatMessage userMessage;
    private ChatMessage assistantMessage;
    private MemoryContext memoryContext;

    @BeforeEach
    void setUp() {
        agentProperties = new AgentProperties(
                true,
                AgentReasoningMode.LOOP,
                new AgentProperties.Cot(0.0, 512),
                new AgentProperties.Planning(true, 0.0, 768),
                new AgentProperties.Loop(6, 0.0, 512),
                new AgentProperties.Subagent(true, 4, true, 2, 2, List.of("search_web", "fetch_webpage"))
        );
        aiModelProperties = new AiModelProperties("qwen3.5-flash", 0.2d, "qwen3-vl-embedding", "qwen3-vl-rerank");
        lenient().when(skillRouterService.route(any())).thenReturn(Optional.empty());
        agentChatService = new AgentChatService(
                agentProperties,
                aiModelProperties,
                authenticatedUserAccessor,
                chatPersistenceService,
                memoryContextAdvisor,
                agentStepPlannerService,
                taskPlanningService,
                agentToolExecutorService,
                agentRagActionService,
                agentRagRoutingService,
                agentExecutionWorkflowService,
                toolPermissionContextFactory,
                toolResolverService,
                skillRouterService,
                skillToolSelector,
                chatAdvisorExecutor,
                chatUsageService,
                springAiChatResponseMapper,
                ragEvidenceGuardService,
                ragAnswerJudgeService
        );
        conversation = new Conversation(
                10L,
                1L,
                "agent-session",
                "Agent Session",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        userMessage = new ChatMessage(
                20L,
                10L,
                1L,
                "user",
                "question",
                "TEXT",
                null,
                OffsetDateTime.now()
        );
        assistantMessage = new ChatMessage(
                21L,
                10L,
                1L,
                "assistant",
                "answer",
                "TEXT",
                "qwen3.5-flash",
                OffsetDateTime.now()
        );
        memoryContext = new MemoryContext(List.of(), List.of(), List.of(), "");
        when(authenticatedUserAccessor.requireUserId(authentication)).thenReturn(1L);
        when(chatPersistenceService.getOrCreateConversation(eq(1L), any())).thenReturn(conversation);
        when(chatPersistenceService.saveUserMessage(eq(1L), eq(10L), any())).thenReturn(userMessage);
    }

    @Test
    void shouldHandleCotMode() {
        AgentChatRequest request = new AgentChatRequest("agent-session", "请简要解释 PostgreSQL 是什么", AgentReasoningMode.COT, null);
        ChatClientResponse clientResponse = buildClientResponse();
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow =
                new AgentExecutionWorkflowService.ExecutionWorkflow(101L, 201L, AgentReasoningMode.COT);

        when(memoryContextAdvisor.buildContext(1L, 10L, request.message())).thenReturn(memoryContext);
        when(agentExecutionWorkflowService.start(1L, conversation, request.message(), AgentReasoningMode.COT))
                .thenReturn(executionWorkflow);
        when(agentStepPlannerService.planCot(request.message(), memoryContext))
                .thenReturn(new AgentStepPlannerService.StructuredResult<>(
                        new AgentCotResult("基于已有知识直接给出简答", "PostgreSQL 是一个开源关系型数据库。"),
                        clientResponse
                ));
        when(springAiChatResponseMapper.toResult(clientResponse.chatResponse()))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult("req-1", "qwen3.5-flash", "ok", 10, 20, 30));
        when(chatPersistenceService.saveAssistantMessage(1L, 10L, "PostgreSQL 是一个开源关系型数据库。", "qwen3.5-flash"))
                .thenReturn(assistantMessage);

        AgentChatResponse response = agentChatService.chat(request, authentication);

        assertThat(response.mode()).isEqualTo(AgentReasoningMode.COT);
        assertThat(response.answer()).isEqualTo("PostgreSQL 是一个开源关系型数据库。");
        assertThat(response.reasoningSummary()).isEqualTo("基于已有知识直接给出简答");
        assertThat(response.sources()).isEmpty();
        verify(agentExecutionWorkflowService).recordPlanningStep(
                1L,
                executionWorkflow,
                1,
                "基于已有知识直接给出简答",
                AgentActionType.FINAL,
                null,
                "PostgreSQL 是一个开源关系型数据库。"
        );
    }

    @Test
    void shouldHandleLoopModeWithRagAction() {
        AgentChatRequest request = new AgentChatRequest("agent-session", "项目里如何使用 rerank？", AgentReasoningMode.LOOP, 3);
        ChatClientResponse clientResponse = buildClientResponse();
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow =
                new AgentExecutionWorkflowService.ExecutionWorkflow(102L, 202L, AgentReasoningMode.LOOP);
        ChatAskResponse.SourceItem sourceItem = new ChatAskResponse.SourceItem(
                64L,
                "ChunkSmokeJsonRetest3",
                "project.retrieval.rerank",
                "project.retrieval.rerank",
                "project.retrieval.rerank",
                6401L,
                0,
                "vector+keyword+rerank",
                0.98d
        );
        RetrievedChunk retrievedChunk = new RetrievedChunk(
                6401L,
                64L,
                "ChunkSmokeJsonRetest3",
                0,
                "rerank model is qwen3-vl-rerank",
                Map.of(),
                0.98d,
                "vector+keyword+rerank"
        );

        when(memoryContextAdvisor.buildContext(1L, 10L, request.message())).thenReturn(memoryContext);
        when(agentExecutionWorkflowService.start(1L, conversation, request.message(), AgentReasoningMode.LOOP))
                .thenReturn(executionWorkflow);
        when(toolPermissionContextFactory.create(authentication)).thenReturn(new PermissionContext(
                1L,
                "chat_user",
                Set.of("CHAT_USER"),
                Set.of(),
                Set.of(),
                Set.of(),
                false
        ));
        when(toolResolverService.resolve(any())).thenReturn(List.of());
        when(taskPlanningService.plan(AgentReasoningMode.LOOP, request.message(), memoryContext, List.of(), null))
                .thenReturn(new TaskPlanningService.StructuredResult<>(
                        new TaskPlan("回答 rerank 问题", "先检索证据，再给出结论", List.of()),
                        clientResponse
                ));
        when(agentStepPlannerService.planNextStep(
                AgentReasoningMode.LOOP,
                request.message(),
                memoryContext,
                List.of(),
                List.of(),
                1,
                3,
                new TaskPlan("回答 rerank 问题", "先检索证据，再给出结论", List.of()),
                null
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "先去知识库拿证据",
                        AgentActionType.RAG,
                        "project.retrieval.rerank",
                        null,
                        null,
                        null
                ),
                clientResponse
        ));
        when(agentStepPlannerService.planNextStep(
                AgentReasoningMode.LOOP,
                request.message(),
                memoryContext,
                List.of(),
                List.of(Map.of(
                        "step", 1,
                        "thought", "先去知识库拿证据",
                        "action", "RAG:project.retrieval.rerank",
                        "observation", "RAG 检索完成。query=project.retrieval.rerank，命中 1 条证据。\n- project.retrieval.rerank | project.retrieval.rerank | vector+keyword+rerank | score=0.9800"
                )),
                2,
                3,
                new TaskPlan("回答 rerank 问题", "先检索证据，再给出结论", List.of()),
                null
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "证据已足够，直接回答",
                        AgentActionType.FINAL,
                        null,
                        null,
                        null,
                        "项目里使用 qwen3-vl-rerank 对向量检索和关键词检索的候选结果做重排。"
                ),
                clientResponse
        ));
        when(agentRagActionService.retrieve("project.retrieval.rerank", 102L))
                .thenReturn(new AgentRagActionService.AgentRagActionResult(
                        "project.retrieval.rerank",
                        List.of(retrievedChunk),
                        List.of(sourceItem),
                        "RAG 检索完成。query=project.retrieval.rerank，命中 1 条证据。\n- project.retrieval.rerank | project.retrieval.rerank | vector+keyword+rerank | score=0.9800"
                ));
        when(ragEvidenceGuardService.assess(eq(request.message()), any()))
                .thenReturn(new RagEvidenceAssessment(true, "enough", 1, 0.98d, 1.0d));
        when(ragAnswerJudgeService.judge(eq(request.message()), any(), any()))
                .thenReturn(new RagAnswerJudgeService.StructuredJudgeResult(
                        new RagAnswerJudgeResult(true, false, "grounded", null),
                        null
                ));
        when(springAiChatResponseMapper.toResult(clientResponse.chatResponse()))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult("req-2", "qwen3.5-flash", "ok", 10, 20, 30));
        when(chatPersistenceService.saveAssistantMessage(
                1L,
                10L,
                "项目里使用 qwen3-vl-rerank 对向量检索和关键词检索的候选结果做重排。",
                "qwen3.5-flash"
        )).thenReturn(assistantMessage);

        AgentChatResponse response = agentChatService.chat(request, authentication);

        assertThat(response.mode()).isEqualTo(AgentReasoningMode.LOOP);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).chunkTitle()).isEqualTo("project.retrieval.rerank");
        verify(chatAdvisorExecutor).execute(argThat(context ->
                context.operation() == AdvisorOperation.KNOWLEDGE_RETRIEVE
                        && "project.retrieval.rerank".equals(context.message())
                        && context.authentication() == authentication
        ));
        verify(agentExecutionWorkflowService).recordRetrieval(
                1L,
                executionWorkflow,
                1,
                "project.retrieval.rerank",
                1,
                "RAG 检索完成。query=project.retrieval.rerank，命中 1 条证据。\n- project.retrieval.rerank | project.retrieval.rerank | vector+keyword+rerank | score=0.9800"
        );
    }

    @Test
    void shouldForceRagOnFirstStepWhenKnowledgeQuestionHeuristicMatches() {
        AgentChatRequest request = new AgentChatRequest("agent-session", "项目里 rerank 模型是什么，如何配置？", AgentReasoningMode.LOOP, 3);
        ChatClientResponse clientResponse = buildClientResponse();
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow =
                new AgentExecutionWorkflowService.ExecutionWorkflow(103L, 203L, AgentReasoningMode.LOOP);
        ChatAskResponse.SourceItem sourceItem = new ChatAskResponse.SourceItem(
                65L,
                "Rerank Guide",
                "Retrieval Rerank",
                "project.retrieval.rerank",
                "project.retrieval.rerank",
                6501L,
                0,
                "vector+keyword+rerank",
                0.96d
        );
        RetrievedChunk retrievedChunk = new RetrievedChunk(
                6501L,
                65L,
                "Rerank Guide",
                0,
                "rerank model is qwen3-vl-rerank",
                Map.of(),
                0.96d,
                "vector+keyword+rerank"
        );

        doReturn(List.<GrantedAuthority>of(
                new SimpleGrantedAuthority(SecurityRole.authority(SecurityRole.KNOWLEDGE_USER))
        )).when(authentication).getAuthorities();
        when(memoryContextAdvisor.buildContext(1L, 10L, request.message())).thenReturn(memoryContext);
        when(agentExecutionWorkflowService.start(1L, conversation, request.message(), AgentReasoningMode.LOOP))
                .thenReturn(executionWorkflow);
        when(toolPermissionContextFactory.create(authentication)).thenReturn(new PermissionContext(
                1L,
                "raguser",
                Set.of("KNOWLEDGE_USER"),
                Set.of(),
                Set.of(),
                Set.of(),
                false
        ));
        when(toolResolverService.resolve(any())).thenReturn(List.of());
        when(taskPlanningService.plan(AgentReasoningMode.LOOP, request.message(), memoryContext, List.of(), null))
                .thenReturn(new TaskPlanningService.StructuredResult<>(
                        new TaskPlan("回答 rerank 配置问题", "先检索证据，再给出结论", List.of()),
                        clientResponse
                ));
        when(agentStepPlannerService.planNextStep(
                AgentReasoningMode.LOOP,
                request.message(),
                memoryContext,
                List.of(),
                List.of(),
                1,
                3,
                new TaskPlan("回答 rerank 配置问题", "先检索证据，再给出结论", List.of()),
                null
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "我记得这个问题和项目配置有关，可以直接回答。",
                        AgentActionType.FINAL,
                        null,
                        null,
                        null,
                        "项目里使用 qwen3-vl-rerank。"
                ),
                clientResponse
        ));
        when(agentStepPlannerService.planNextStep(
                AgentReasoningMode.LOOP,
                request.message(),
                memoryContext,
                List.of(),
                List.of(Map.of(
                        "step", 1,
                        "thought", "我记得这个问题和项目配置有关，可以直接回答。 知识型问题启发式命中：命中知识型问题词 + 命中领域事实词 + 命中结构化标识",
                        "action", "RAG:项目里 rerank 模型是什么，如何配置？",
                        "observation", "RAG 检索完成。query=项目里 rerank 模型是什么，如何配置？，命中 1 条证据。\n- Retrieval Rerank | project.retrieval.rerank | vector+keyword+rerank | score=0.9600"
                )),
                2,
                3,
                new TaskPlan("回答 rerank 配置问题", "先检索证据，再给出结论", List.of()),
                null
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "证据已确认，直接回答。",
                        AgentActionType.FINAL,
                        null,
                        null,
                        null,
                        "项目里使用 qwen3-vl-rerank 对候选结果做重排。"
                ),
                clientResponse
        ));
        when(agentRagRoutingService.decide(request, memoryContext, 103L)).thenReturn(
                AgentRagRoutingService.RagRoutingDecision.force(
                        "heuristic",
                        "知识型问题启发式命中：命中知识型问题词 + 命中领域事实词 + 命中结构化标识",
                        request.message(),
                        null
                )
        );
        when(agentRagActionService.retrieve(request.message(), 103L))
                .thenReturn(new AgentRagActionService.AgentRagActionResult(
                        request.message(),
                        List.of(retrievedChunk),
                        List.of(sourceItem),
                        "RAG 检索完成。query=项目里 rerank 模型是什么，如何配置？，命中 1 条证据。\n- Retrieval Rerank | project.retrieval.rerank | vector+keyword+rerank | score=0.9600"
                ));
        when(ragEvidenceGuardService.assess(eq(request.message()), any()))
                .thenReturn(new RagEvidenceAssessment(true, "enough", 1, 0.96d, 1.0d));
        when(ragAnswerJudgeService.judge(eq(request.message()), any(), any()))
                .thenReturn(new RagAnswerJudgeService.StructuredJudgeResult(
                        new RagAnswerJudgeResult(true, false, "grounded", null),
                        null
                ));
        when(springAiChatResponseMapper.toResult(clientResponse.chatResponse()))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult("req-3", "qwen3.5-flash", "ok", 10, 20, 30));
        when(chatPersistenceService.saveAssistantMessage(
                1L,
                10L,
                "项目里使用 qwen3-vl-rerank 对候选结果做重排。",
                "qwen3.5-flash"
        )).thenReturn(assistantMessage);

        AgentChatResponse response = agentChatService.chat(request, authentication);

        assertThat(response.answer()).isEqualTo("项目里使用 qwen3-vl-rerank 对候选结果做重排。");
        assertThat(response.sources()).hasSize(1);
        verify(agentRagActionService).retrieve(request.message(), 103L);
        verify(chatAdvisorExecutor).execute(argThat(context ->
                context.operation() == AdvisorOperation.KNOWLEDGE_RETRIEVE
                        && request.message().equals(context.message())
        ));
    }

    @Test
    void shouldHandleReactModeWithDirectToolReturn() {
        AgentChatRequest request = new AgentChatRequest(
                "agent-session",
                "请使用 generate_pdf 工具生成一个 PDF",
                AgentReasoningMode.REACT,
                3
        );
        ChatClientResponse clientResponse = buildClientResponse();
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow =
                new AgentExecutionWorkflowService.ExecutionWorkflow(103L, 203L, AgentReasoningMode.REACT);
        TaskPlan taskPlan = new TaskPlan(
                "生成 PDF",
                "先准备内容，再调用 PDF 工具生成文件",
                List.of(new TaskPlanStep(
                        "step-1",
                        "调用 PDF 工具",
                        "使用 generate_pdf 输出最终文件",
                        List.of(),
                        List.of("generate_pdf"),
                        false,
                        "拿到 PDF 生成结果"
                ))
        );
        PermissionContext permissionContext = new PermissionContext(
                1L,
                "chat_user",
                Set.of("CHAT_USER"),
                Set.of("generate_pdf"),
                Set.of(),
                Set.of(),
                false
        );
        RegisteredTool registeredTool = new RegisteredTool(
                new PlatformToolDefinition(
                        "generate_pdf",
                        "generate_pdf",
                        "PDF 生成",
                        "生成 PDF",
                        "{}",
                        true,
                        false,
                        true,
                        false,
                        true,
                        false,
                        30_000L,
                        ToolRiskLevel.MEDIUM,
                        Set.of("CHAT_USER"),
                        List.of("pdf"),
                        List.of("chat")
                ),
                toolCallback
        );

        when(memoryContextAdvisor.buildContext(1L, 10L, request.message())).thenReturn(memoryContext);
        when(agentExecutionWorkflowService.start(1L, conversation, request.message(), AgentReasoningMode.REACT))
                .thenReturn(executionWorkflow);
        when(toolPermissionContextFactory.create(authentication)).thenReturn(permissionContext);
        when(toolResolverService.resolve(any())).thenReturn(List.of(registeredTool));
        when(taskPlanningService.plan(AgentReasoningMode.REACT, request.message(), memoryContext, List.of(registeredTool), null))
                .thenReturn(new TaskPlanningService.StructuredResult<>(taskPlan, clientResponse));
        when(agentStepPlannerService.planNextStep(
                AgentReasoningMode.REACT,
                request.message(),
                memoryContext,
                List.of(registeredTool),
                List.of(),
                1,
                3,
                taskPlan,
                null
        )).thenReturn(new AgentStepPlannerService.StructuredResult<>(
                new AgentStepPlan(
                        "先调用 PDF 工具直接生成结果",
                        AgentActionType.TOOL,
                        null,
                        "generate_pdf",
                        Map.of("title", "Agent Smoke", "content", "Hello Agent PDF"),
                        null
                ),
                clientResponse
        ));
        when(springAiChatResponseMapper.toResult(clientResponse.chatResponse()))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult("req-3", "qwen3.5-flash", "ok", 10, 20, 30));
        when(agentToolExecutorService.isAvailableTool("generate_pdf", List.of(registeredTool))).thenReturn(true);
        when(agentToolExecutorService.execute(
                "generate_pdf",
                Map.of("title", "Agent Smoke", "content", "Hello Agent PDF"),
                permissionContext,
                conversation,
                103L,
                203L,
                1
        )).thenReturn(new AgentToolExecutorService.ToolExecutionOutcome("generate_pdf", "PDF generated", true));
        when(springAiChatResponseMapper.toResult(isNull(), eq("qwen3.5-flash"), eq("PDF generated"), isNull(), isNull(), isNull()))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult(null, "qwen3.5-flash", "PDF generated", null, null, null));
        when(chatPersistenceService.saveAssistantMessage(1L, 10L, "PDF generated", "qwen3.5-flash"))
                .thenReturn(assistantMessage);

        AgentChatResponse response = agentChatService.chat(request, authentication);

        assertThat(response.mode()).isEqualTo(AgentReasoningMode.REACT);
        assertThat(response.answer()).isEqualTo("PDF generated");
        assertThat(response.toolNames()).containsExactly("generate_pdf");
        assertThat(response.stepCount()).isEqualTo(1);
        verify(agentExecutionWorkflowService).recordTaskPlan(1L, executionWorkflow, taskPlan);
    }

    private ChatClientResponse buildClientResponse() {
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        return new ChatClientResponse(chatResponse, Map.of());
    }
}
