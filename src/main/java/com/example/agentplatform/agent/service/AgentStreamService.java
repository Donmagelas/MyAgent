package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.AgentStreamingExecutionPlan;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.observability.domain.ModelUsageRecord;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 流式服务。
 * 负责把统一 Agent Loop 的步骤事件、usage 记录和最终答案流式转换成 SSE 事件。
 */
@Service
public class AgentStreamService {

    private final AgentChatService agentChatService;
    private final AgentFinalAnswerStreamService agentFinalAnswerStreamService;

    public AgentStreamService(
            AgentChatService agentChatService,
            AgentFinalAnswerStreamService agentFinalAnswerStreamService
    ) {
        this.agentChatService = agentChatService;
        this.agentFinalAnswerStreamService = agentFinalAnswerStreamService;
    }

    /**
     * 发起一次 Agent 流式执行。
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(AgentChatRequest request, Authentication authentication) {
        return Flux.create(sink -> execute(request, authentication, sink), FluxSink.OverflowStrategy.BUFFER);
    }

    private void execute(
            AgentChatRequest request,
            Authentication authentication,
            FluxSink<ServerSentEvent<ChatStreamEvent>> sink
    ) {
        AtomicReference<Long> conversationIdRef = new AtomicReference<>();
        AtomicReference<String> sessionIdRef = new AtomicReference<>();
        AtomicReference<String> modeRef = new AtomicReference<>("agent");

        AgentExecutionListener listener = new AgentExecutionListener() {
            @Override
            public void onStart(
                    AgentReasoningMode mode,
                    Long conversationId,
                    String sessionId,
                    Long workflowId
            ) {
                conversationIdRef.set(conversationId);
                sessionIdRef.set(sessionId);
                modeRef.set(resolveMode(mode));
                sink.next(toSse(ChatStreamEvent.start(
                        modeRef.get(),
                        conversationId,
                        sessionId,
                        Map.of("workflowId", workflowId)
                )));
                sink.next(toSse(ChatStreamEvent.step(
                        "info",
                        modeRef.get(),
                        conversationId,
                        sessionId,
                        "已进入统一 Agent Loop",
                        Map.of("workflowId", workflowId)
                )));
            }

            @Override
            public void onTaskPlan(TaskPlan taskPlan) {
                String content = "已生成任务计划：" + safe(taskPlan.planSummary())
                        + "；步骤数=" + (taskPlan.steps() == null ? 0 : taskPlan.steps().size());
                sink.next(toSse(ChatStreamEvent.step(
                        "task-plan",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        content,
                        buildTaskPlanMetadata(taskPlan)
                )));
            }

            @Override
            public void onPlanning(int stepIndex, String thought, AgentActionType actionType, String toolName) {
                String action = actionType == null ? "UNKNOWN" : actionType.name();
                String content = "第 " + stepIndex + " 步推理："
                        + safe(thought)
                        + "；动作=" + action
                        + (toolName == null || toolName.isBlank() ? "" : "；工具=" + toolName);
                sink.next(toSse(ChatStreamEvent.step(
                        "plan",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        content,
                        buildPlanningMetadata(stepIndex, actionType, toolName)
                )));
            }

            @Override
            public void onRetrieval(int stepIndex, String query, List<ChatAskResponse.SourceItem> sources) {
                sink.next(toSse(ChatStreamEvent.sources(
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        sources
                )));
                sink.next(toSse(ChatStreamEvent.step(
                        "rag",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        "第 " + stepIndex + " 步已完成知识检索；query=" + query
                                + "；命中=" + (sources == null ? 0 : sources.size()),
                        Map.of(
                                "step", stepIndex,
                                "query", query,
                                "sourceCount", sources == null ? 0 : sources.size()
                        )
                )));
            }

            @Override
            public void onObservation(int stepIndex, String toolName, String observation) {
                String content = "第 " + stepIndex + " 步观察："
                        + (toolName == null || toolName.isBlank() ? "" : "[" + toolName + "] ")
                        + safe(observation);
                sink.next(toSse(ChatStreamEvent.step(
                        "observation",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        content,
                        buildObservationMetadata(stepIndex, toolName)
                )));
            }

            @Override
            public void onEvidenceGate(int stepIndex, String reason, String stepName) {
                sink.next(toSse(ChatStreamEvent.step(
                        "evidence-gate",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        "第 " + stepIndex + " 步因检索证据不足被拦截，已降级为证据不足答复：" + safe(reason),
                        buildDecisionMetadata(stepIndex, stepName)
                )));
            }

            @Override
            public void onJudgeDowngrade(int stepIndex, String reason, String stepName) {
                sink.next(toSse(ChatStreamEvent.step(
                        "judge",
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        "第 " + stepIndex + " 步回答后校验未通过，已自动降级为证据不足答复：" + safe(reason),
                        buildDecisionMetadata(stepIndex, stepName)
                )));
            }

            @Override
            public void onUsage(ModelUsageRecord record) {
                sink.next(toSse(ChatStreamEvent.usage(
                        modeRef.get(),
                        conversationIdRef.get(),
                        sessionIdRef.get(),
                        new ChatStreamEvent.Usage(
                                record.requestId(),
                                record.modelName(),
                                record.promptTokens(),
                                record.completionTokens(),
                                record.totalTokens(),
                                record.latencyMs()
                        ),
                        buildUsageMetadata(record)
                )));
            }
        };

        reactor.core.publisher.Mono.fromCallable(() -> agentChatService.prepareStreamingExecution(request, authentication, listener))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> continueFinalStream(plan, listener))
                .subscribe(sink::next, throwable -> {
                    sink.next(toSse(ChatStreamEvent.error(
                            modeRef.get(),
                            conversationIdRef.get(),
                            sessionIdRef.get(),
                            throwable.getMessage()
                    )));
                    sink.complete();
                }, sink::complete);
    }

    private String resolveMode(AgentReasoningMode mode) {
        return switch (mode) {
            case COT -> "agent-cot";
            case REACT -> "agent-react";
            case LOOP -> "agent-loop";
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> buildTaskPlanMetadata(TaskPlan taskPlan) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("goal", taskPlan.goal());
        metadata.put("planSummary", taskPlan.planSummary());
        metadata.put("stepCount", taskPlan.steps() == null ? 0 : taskPlan.steps().size());
        return metadata;
    }

    private Map<String, Object> buildPlanningMetadata(
            int stepIndex,
            AgentActionType actionType,
            String toolName
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step", stepIndex);
        metadata.put("actionType", actionType == null ? null : actionType.name());
        if (toolName != null && !toolName.isBlank()) {
            metadata.put("toolName", toolName);
        }
        return metadata;
    }

    private Map<String, Object> buildObservationMetadata(int stepIndex, String toolName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step", stepIndex);
        if (toolName != null && !toolName.isBlank()) {
            metadata.put("toolName", toolName);
        }
        return metadata;
    }

    private Map<String, Object> buildDecisionMetadata(int stepIndex, String stepName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("step", stepIndex);
        if (stepName != null && !stepName.isBlank()) {
            metadata.put("stepName", stepName);
        }
        return metadata;
    }

    private Map<String, Object> buildUsageMetadata(ModelUsageRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", record.workflowId());
        metadata.put("taskId", record.taskId());
        metadata.put("stepName", record.stepName());
        metadata.put("success", record.success());
        if (record.errorMessage() != null && !record.errorMessage().isBlank()) {
            metadata.put("errorMessage", record.errorMessage());
        }
        return metadata;
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> continueFinalStream(
            AgentStreamingExecutionPlan plan,
            AgentExecutionListener executionListener
    ) {
        return agentFinalAnswerStreamService.streamFinalAnswer(plan, executionListener);
    }
}
