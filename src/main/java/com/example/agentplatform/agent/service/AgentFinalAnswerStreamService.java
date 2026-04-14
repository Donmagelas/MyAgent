package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentStreamingExecutionPlan;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.chat.service.ChatCompletionClient;
import com.example.agentplatform.chat.service.ChatCompletionStreamClient;
import com.example.agentplatform.chat.service.ChatPersistenceService;
import com.example.agentplatform.chat.service.ChatUsageService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.service.RagAnswerJudgeService;
import com.example.agentplatform.rag.service.RagEvidenceGuardService;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent 最终回答流式生成服务。
 * 在统一 Agent Loop 完成步骤决策后，负责真正调用模型产生增量 delta，而不是一次性返回整段答案。
 */
@Service
public class AgentFinalAnswerStreamService {

    private final AgentPromptService agentPromptService;
    private final ChatCompletionStreamClient chatCompletionStreamClient;
    private final ChatPersistenceService chatPersistenceService;
    private final ChatUsageService chatUsageService;
    private final AgentExecutionWorkflowService agentExecutionWorkflowService;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final AiModelProperties aiModelProperties;
    private final RagEvidenceGuardService ragEvidenceGuardService;
    private final RagAnswerJudgeService ragAnswerJudgeService;

    public AgentFinalAnswerStreamService(
            AgentPromptService agentPromptService,
            ChatCompletionStreamClient chatCompletionStreamClient,
            ChatPersistenceService chatPersistenceService,
            ChatUsageService chatUsageService,
            AgentExecutionWorkflowService agentExecutionWorkflowService,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            AiModelProperties aiModelProperties,
            RagEvidenceGuardService ragEvidenceGuardService,
            RagAnswerJudgeService ragAnswerJudgeService
    ) {
        this.agentPromptService = agentPromptService;
        this.chatCompletionStreamClient = chatCompletionStreamClient;
        this.chatPersistenceService = chatPersistenceService;
        this.chatUsageService = chatUsageService;
        this.agentExecutionWorkflowService = agentExecutionWorkflowService;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.aiModelProperties = aiModelProperties;
        this.ragEvidenceGuardService = ragEvidenceGuardService;
        this.ragAnswerJudgeService = ragAnswerJudgeService;
    }

    /**
     * 基于准备好的执行计划流式输出最终回答。
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> streamFinalAnswer(
            AgentStreamingExecutionPlan executionPlan,
            AgentExecutionListener executionListener
    ) {
        RagEvidenceAssessment evidenceAssessment = ragEvidenceGuardService.assess(
                executionPlan.originalMessage(),
                executionPlan.retrievedChunks()
        );
        if (!executionPlan.retrievedChunks().isEmpty() && !evidenceAssessment.sufficient()) {
            executionListener.onEvidenceGate(
                    executionPlan.stepCount(),
                    evidenceAssessment.reason(),
                    executionPlan.usageStepName() + "-evidence-gate"
            );
            return Mono.fromCallable(() -> finalizeEvidenceInsufficient(executionPlan, executionListener, evidenceAssessment))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapIterable(events -> events);
        }

        if (executionPlan.directReturn()) {
            return Mono.fromCallable(() -> finalizeDirectReturn(executionPlan, executionListener))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapIterable(events -> events);
        }

        String systemPrompt = agentPromptService.buildFinalAnswerSystemPrompt(
                executionPlan.memoryContext(),
                executionPlan.sources()
        );
        String userPrompt = agentPromptService.buildFinalAnswerUserPrompt(
                executionPlan.originalMessage(),
                executionPlan.answerDraft(),
                executionPlan.reasoningSummary(),
                executionPlan.sources()
        );
        StreamingState state = new StreamingState(executionPlan, aiModelProperties.chatModel());

        Flux<ServerSentEvent<ChatStreamEvent>> deltas = chatCompletionStreamClient.stream(systemPrompt, userPrompt)
                .handle((chunk, sink) -> {
                    state.capture(chunk);
                    if (chunk.delta() != null && !chunk.delta().isBlank()) {
                        sink.next(toSse(ChatStreamEvent.delta(
                                resolveMode(executionPlan),
                                executionPlan.conversation().id(),
                                executionPlan.conversation().sessionId(),
                                chunk.delta()
                        )));
                    }
                });

        Flux<ServerSentEvent<ChatStreamEvent>> successTail = Mono.fromCallable(
                        () -> finalizeStreamSuccess(executionPlan, state, executionListener)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(events -> events);

        return Flux.concat(deltas, successTail)
                .onErrorResume(throwable -> Mono.fromCallable(() -> finalizeFailure(executionPlan, throwable))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapIterable(events -> events));
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeDirectReturn(
            AgentStreamingExecutionPlan executionPlan,
            AgentExecutionListener executionListener
    ) {
        var assistantMessage = chatPersistenceService.saveAssistantMessage(
                executionPlan.userId(),
                executionPlan.conversation().id(),
                executionPlan.answerDraft(),
                aiModelProperties.chatModel()
        );
        var usageRecord = chatUsageService.save(
                executionPlan.executionWorkflow().workflowId(),
                null,
                executionPlan.conversation().id(),
                assistantMessage.id(),
                executionPlan.usageStepName(),
                springAiChatResponseMapper.toResult(
                        null,
                        aiModelProperties.chatModel(),
                        executionPlan.answerDraft(),
                        null,
                        null,
                        null
                ),
                0L,
                true,
                null
        );
        executionListener.onUsage(usageRecord);
        agentExecutionWorkflowService.completeSuccess(
                executionPlan.userId(),
                executionPlan.executionWorkflow(),
                executionPlan.answerDraft(),
                executionPlan.reasoningSummary(),
                executionPlan.stepCount(),
                executionPlan.toolNames()
        );
        return List.of(
                toSse(ChatStreamEvent.delta(
                        resolveMode(executionPlan),
                        executionPlan.conversation().id(),
                        executionPlan.conversation().sessionId(),
                        executionPlan.answerDraft()
                )),
                toSse(ChatStreamEvent.done(
                        resolveMode(executionPlan),
                        executionPlan.conversation().id(),
                        executionPlan.conversation().sessionId(),
                        executionPlan.answerDraft(),
                        new ChatStreamEvent.Usage(
                                usageRecord.requestId(),
                                usageRecord.modelName(),
                                usageRecord.promptTokens(),
                                usageRecord.completionTokens(),
                                usageRecord.totalTokens(),
                                usageRecord.latencyMs()
                        )
                ))
        );
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeStreamSuccess(
            AgentStreamingExecutionPlan executionPlan,
            StreamingState state,
            AgentExecutionListener executionListener
    ) {
        RagAnswerJudgeService.StructuredJudgeResult judgeResult =
                ragAnswerJudgeService.judge(executionPlan.originalMessage(), state.answer(), executionPlan.retrievedChunks());
        if (judgeResult.response() != null) {
            var judgeUsage = chatUsageService.save(
                    executionPlan.executionWorkflow().workflowId(),
                    null,
                    executionPlan.conversation().id(),
                    null,
                    executionPlan.usageStepName() + "-judge",
                    springAiChatResponseMapper.toResult(judgeResult.response().chatResponse()),
                    Duration.between(state.startedAt(), Instant.now()).toMillis(),
                    true,
                    null
            );
            executionListener.onUsage(judgeUsage);
        }
        String finalAnswer = resolveFinalAnswer(state.answer(), judgeResult);
        if (judgeResult.body() != null && judgeResult.body().downgradeToInsufficient()) {
            executionListener.onJudgeDowngrade(
                    executionPlan.stepCount(),
                    judgeResult.body().reason(),
                    executionPlan.usageStepName() + "-judge"
            );
        }
        var assistantMessage = chatPersistenceService.saveAssistantMessage(
                executionPlan.userId(),
                executionPlan.conversation().id(),
                finalAnswer,
                state.modelName()
        );
        long latencyMs = Duration.between(state.startedAt(), Instant.now()).toMillis();
        ChatCompletionClient.ChatCompletionResult result = new ChatCompletionClient.ChatCompletionResult(
                state.requestId(),
                state.modelName(),
                finalAnswer,
                state.promptTokens(),
                state.completionTokens(),
                state.totalTokens()
        );
        var usageRecord = chatUsageService.save(
                executionPlan.executionWorkflow().workflowId(),
                null,
                executionPlan.conversation().id(),
                assistantMessage.id(),
                executionPlan.usageStepName(),
                result,
                latencyMs,
                true,
                null
        );
        executionListener.onUsage(usageRecord);
        agentExecutionWorkflowService.completeSuccess(
                executionPlan.userId(),
                executionPlan.executionWorkflow(),
                finalAnswer,
                executionPlan.reasoningSummary(),
                executionPlan.stepCount(),
                executionPlan.toolNames()
        );
        return List.of(toSse(ChatStreamEvent.done(
                resolveMode(executionPlan),
                executionPlan.conversation().id(),
                executionPlan.conversation().sessionId(),
                finalAnswer,
                new ChatStreamEvent.Usage(
                        state.requestId(),
                        state.modelName(),
                        state.promptTokens(),
                        state.completionTokens(),
                        state.totalTokens(),
                        latencyMs
                )
        )));
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeEvidenceInsufficient(
            AgentStreamingExecutionPlan executionPlan,
            AgentExecutionListener executionListener,
            RagEvidenceAssessment evidenceAssessment
    ) {
        String fallbackAnswer = ragEvidenceGuardService.buildInsufficientAnswer();
        var assistantMessage = chatPersistenceService.saveAssistantMessage(
                executionPlan.userId(),
                executionPlan.conversation().id(),
                fallbackAnswer,
                aiModelProperties.chatModel()
        );
        var usageRecord = chatUsageService.save(
                executionPlan.executionWorkflow().workflowId(),
                null,
                executionPlan.conversation().id(),
                assistantMessage.id(),
                executionPlan.usageStepName() + "-evidence-gate",
                null,
                0L,
                true,
                evidenceAssessment.reason()
        );
        executionListener.onUsage(usageRecord);
        agentExecutionWorkflowService.completeSuccess(
                executionPlan.userId(),
                executionPlan.executionWorkflow(),
                fallbackAnswer,
                executionPlan.reasoningSummary(),
                executionPlan.stepCount(),
                executionPlan.toolNames()
        );
        return List.of(
                toSse(ChatStreamEvent.delta(
                        resolveMode(executionPlan),
                        executionPlan.conversation().id(),
                        executionPlan.conversation().sessionId(),
                        fallbackAnswer
                )),
                toSse(ChatStreamEvent.done(
                        resolveMode(executionPlan),
                        executionPlan.conversation().id(),
                        executionPlan.conversation().sessionId(),
                        fallbackAnswer,
                        null
                ))
        );
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeFailure(
            AgentStreamingExecutionPlan executionPlan,
            Throwable throwable
    ) {
        agentExecutionWorkflowService.completeFailure(
                executionPlan.userId(),
                executionPlan.executionWorkflow(),
                executionPlan.stepCount(),
                throwable instanceof Exception exception ? exception : new RuntimeException(throwable)
        );
        return List.of(toSse(ChatStreamEvent.error(
                resolveMode(executionPlan),
                executionPlan.conversation().id(),
                executionPlan.conversation().sessionId(),
                throwable.getMessage()
        )));
    }

    private String resolveFinalAnswer(
            String answer,
            RagAnswerJudgeService.StructuredJudgeResult judgeResult
    ) {
        if (judgeResult == null || judgeResult.body() == null) {
            return answer;
        }
        if (!judgeResult.body().downgradeToInsufficient()) {
            return answer;
        }
        String safeAnswer = judgeResult.body().safeAnswer();
        return safeAnswer == null || safeAnswer.isBlank()
                ? ragEvidenceGuardService.buildInsufficientAnswer()
                : safeAnswer;
    }

    private String resolveMode(AgentStreamingExecutionPlan executionPlan) {
        return switch (executionPlan.mode()) {
            case COT -> "agent-cot";
            case REACT -> "agent-react";
            case LOOP -> "agent-loop";
        };
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }

    /**
     * 流式最终回答聚合状态。
     */
    private static final class StreamingState {

        private final Instant startedAt;
        private final StringBuilder answer;
        private String requestId;
        private String modelName;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        private StreamingState(AgentStreamingExecutionPlan executionPlan, String defaultModelName) {
            this.startedAt = Instant.now();
            this.answer = new StringBuilder();
            this.modelName = defaultModelName;
        }

        private void capture(ChatCompletionStreamClient.ChatCompletionChunk chunk) {
            if (chunk.requestId() != null && !chunk.requestId().isBlank()) {
                this.requestId = chunk.requestId();
            }
            if (chunk.modelName() != null && !chunk.modelName().isBlank()) {
                this.modelName = chunk.modelName();
            }
            if (chunk.promptTokens() != null) {
                this.promptTokens = chunk.promptTokens();
            }
            if (chunk.completionTokens() != null) {
                this.completionTokens = chunk.completionTokens();
            }
            if (chunk.totalTokens() != null) {
                this.totalTokens = chunk.totalTokens();
            }
            if (chunk.delta() != null && !chunk.delta().isBlank()) {
                this.answer.append(chunk.delta());
            }
        }

        private Instant startedAt() {
            return startedAt;
        }

        private String answer() {
            return answer.toString();
        }

        private String requestId() {
            return requestId;
        }

        private String modelName() {
            return modelName;
        }

        private Integer promptTokens() {
            return promptTokens;
        }

        private Integer completionTokens() {
            return completionTokens;
        }

        private Integer totalTokens() {
            return totalTokens;
        }
    }
}
