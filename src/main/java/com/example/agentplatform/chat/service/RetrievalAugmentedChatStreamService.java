package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.advisor.MemoryContextAdvisor;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.rag.service.RagAnswerJudgeService;
import com.example.agentplatform.rag.service.RagEvidenceGuardService;
import com.example.agentplatform.rag.service.SpringAiRagAdvisorFactory;
import com.example.agentplatform.rag.service.SpringAiRetrievedDocumentMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 检索增强流式聊天服务。
 * 使用 RetrievalAugmentationAdvisor 在流式回答前完成查询增强与检索上下文拼装。
 */
@Service
public class RetrievalAugmentedChatStreamService {

    private static final String MODE = "grounded";
    private static final String STEP_NAME = "chat-grounded-stream";

    private final ChatPersistenceService chatPersistenceService;
    private final SpringAiQuestionAnswerPromptService springAiQuestionAnswerPromptService;
    private final ChatUsageService chatUsageService;
    private final MemoryContextAdvisor memoryContextAdvisor;
    private final SpringAiRagAdvisorFactory springAiRagAdvisorFactory;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final RagEvidenceGuardService ragEvidenceGuardService;
    private final RagAnswerJudgeService ragAnswerJudgeService;
    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;

    public RetrievalAugmentedChatStreamService(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            ChatPersistenceService chatPersistenceService,
            SpringAiQuestionAnswerPromptService springAiQuestionAnswerPromptService,
            ChatUsageService chatUsageService,
            MemoryContextAdvisor memoryContextAdvisor,
            SpringAiRagAdvisorFactory springAiRagAdvisorFactory,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            RagEvidenceGuardService ragEvidenceGuardService,
            RagAnswerJudgeService ragAnswerJudgeService
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.chatPersistenceService = chatPersistenceService;
        this.springAiQuestionAnswerPromptService = springAiQuestionAnswerPromptService;
        this.chatUsageService = chatUsageService;
        this.memoryContextAdvisor = memoryContextAdvisor;
        this.springAiRagAdvisorFactory = springAiRagAdvisorFactory;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.ragEvidenceGuardService = ragEvidenceGuardService;
        this.ragAnswerJudgeService = ragAnswerJudgeService;
    }

    /**
     * 流式输出一次 grounded chat 响应。
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(ChatAskRequest request, List<RetrievedChunk> chunks, Long userId) {
        return Mono.fromCallable(() -> prepareSession(userId, request, chunks))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(preparedSession -> doStream(request, preparedSession));
    }

    private PreparedStreamingSession prepareSession(Long userId, ChatAskRequest request, List<RetrievedChunk> chunks) {
        var conversation = chatPersistenceService.getOrCreateConversation(userId, request);
        var userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());
        MemoryContext memoryContext = memoryContextAdvisor.buildContext(userId, conversation.id(), request.message());
        String systemPrompt = springAiQuestionAnswerPromptService.buildSystemPrompt(memoryContext);
        List<ChatAskResponse.SourceItem> sources = springAiRetrievedDocumentMapper.toSourceItems(chunks);
        return new PreparedStreamingSession(conversation, userMessage, systemPrompt, sources, chunks);
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> doStream(ChatAskRequest request, PreparedStreamingSession preparedSession) {
        RagEvidenceAssessment evidenceAssessment =
                ragEvidenceGuardService.assess(request.message(), preparedSession.retrievedChunks());
        if (!evidenceAssessment.sufficient()) {
            return Mono.fromCallable(() -> finalizeEvidenceInsufficient(preparedSession, evidenceAssessment))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(Flux::fromIterable);
        }

        StreamingState state = new StreamingState(
                preparedSession.conversation(),
                preparedSession.userMessage(),
                preparedSession.sources(),
                preparedSession.retrievedChunks(),
                aiModelProperties.chatModel()
        );

        Flux<ServerSentEvent<ChatStreamEvent>> prefix = Flux.fromIterable(buildPrefixEvents(state));
        Flux<ServerSentEvent<ChatStreamEvent>> deltas = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(aiModelProperties.chatTemperature())
                        .build())
                .advisors(springAiRagAdvisorFactory.createRetrievalAugmentationAdvisor())
                .system(preparedSession.systemPrompt())
                .user(request.message())
                .stream()
                .chatClientResponse()
                .flatMapIterable(chatClientResponse -> {
                    List<ServerSentEvent<ChatStreamEvent>> events = new ArrayList<>();
                    state.capture(chatClientResponse.chatResponse());
                    if (!state.sourcesLoaded()) {
                        List<RetrievedChunk> actualChunks = springAiRetrievedDocumentMapper.fromAdvisorContext(chatClientResponse.context());
                        if (actualChunks.isEmpty()) {
                            actualChunks = springAiRetrievedDocumentMapper.fromResponseMetadata(
                                    chatClientResponse.chatResponse().getMetadata()
                            );
                        }
                        List<RetrievedChunk> sourceChunks = springAiRetrievedDocumentMapper.mergeWithFallback(
                                actualChunks,
                                preparedSession.retrievedChunks()
                        );
                        if (!sourceChunks.isEmpty()) {
                            state.replaceSources(springAiRetrievedDocumentMapper.toSourceItems(sourceChunks));
                            state.replaceRetrievedChunks(sourceChunks);
                            state.markSourcesLoaded();
                            events.add(toSse(ChatStreamEvent.sources(
                                    MODE,
                                    state.conversationId(),
                                    state.sessionId(),
                                    state.sources()
                            )));
                        }
                    }
                    String delta = state.lastDelta();
                    if (!delta.isBlank()) {
                        events.add(toSse(ChatStreamEvent.delta(
                                MODE,
                                state.conversationId(),
                                state.sessionId(),
                                delta
                        )));
                    }
                    return events;
                });

        Flux<ServerSentEvent<ChatStreamEvent>> successTail = Mono.fromCallable(() -> finalizeSuccess(state))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(events -> events);

        return Flux.concat(prefix, deltas, successTail)
                .onErrorResume(throwable -> Mono.fromCallable(() -> finalizeFailure(state, throwable))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flux());
    }

    private List<ServerSentEvent<ChatStreamEvent>> buildPrefixEvents(StreamingState state) {
        List<ServerSentEvent<ChatStreamEvent>> events = new ArrayList<>();
        events.add(toSse(ChatStreamEvent.start(MODE, state.conversationId(), state.sessionId())));
        return events;
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeSuccess(StreamingState state) {
        RagAnswerJudgeService.StructuredJudgeResult judgeResult =
                ragAnswerJudgeService.judge(state.originalQuestion(), state.answer(), state.retrievedChunks());
        List<ServerSentEvent<ChatStreamEvent>> events = new ArrayList<>();
        if (judgeResult.response() != null) {
            chatUsageService.save(
                    state.conversationId(),
                    state.userMessage().id(),
                    STEP_NAME + "-judge",
                    springAiChatResponseMapper.toResult(judgeResult.response().chatResponse()),
                    Duration.between(state.startTime(), Instant.now()).toMillis(),
                    true,
                    null
            );
        }
        String finalAnswer = resolveFinalAnswer(state.answer(), judgeResult);
        if (judgeResult.body() != null && judgeResult.body().downgradeToInsufficient()) {
            events.add(toSse(ChatStreamEvent.step(
                    "judge",
                    MODE,
                    state.conversationId(),
                    state.sessionId(),
                    "回答后校验未通过，已自动降级为证据不足答复：" + judgeResult.body().reason(),
                    java.util.Map.of("stepName", STEP_NAME + "-judge")
            )));
        }
        var assistantMessage = chatPersistenceService.saveAssistantMessage(
                state.userId(),
                state.conversationId(),
                finalAnswer,
                state.modelName()
        );
        long latencyMs = Duration.between(state.startTime(), Instant.now()).toMillis();
        ChatCompletionClient.ChatCompletionResult result = springAiChatResponseMapper.toResult(
                state.requestId(),
                state.modelName(),
                finalAnswer,
                state.promptTokens(),
                state.completionTokens(),
                state.totalTokens()
        );
        chatUsageService.save(
                state.conversationId(),
                assistantMessage.id(),
                STEP_NAME,
                result,
                latencyMs,
                true,
                null
        );
        events.add(toSse(ChatStreamEvent.done(
                MODE,
                state.conversationId(),
                state.sessionId(),
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
        return events;
    }

    private ServerSentEvent<ChatStreamEvent> finalizeFailure(StreamingState state, Throwable throwable) {
        long latencyMs = Duration.between(state.startTime(), Instant.now()).toMillis();
        chatUsageService.save(
                state.conversationId(),
                state.userMessage().id(),
                STEP_NAME,
                null,
                latencyMs,
                false,
                throwable.getMessage()
        );
        return toSse(ChatStreamEvent.error(MODE, state.conversationId(), state.sessionId(), throwable.getMessage()));
    }

    private List<ServerSentEvent<ChatStreamEvent>> finalizeEvidenceInsufficient(
            PreparedStreamingSession preparedSession,
            RagEvidenceAssessment evidenceAssessment
    ) {
        String fallbackAnswer = ragEvidenceGuardService.buildInsufficientAnswer();
        var assistantMessage = chatPersistenceService.saveAssistantMessage(
                preparedSession.conversation().userId(),
                preparedSession.conversation().id(),
                fallbackAnswer,
                aiModelProperties.chatModel()
        );
        chatUsageService.save(
                preparedSession.conversation().id(),
                assistantMessage.id(),
                STEP_NAME + "-evidence-gate",
                null,
                0L,
                true,
                evidenceAssessment.reason()
        );
        return List.of(
                toSse(ChatStreamEvent.start(MODE, preparedSession.conversation().id(), preparedSession.conversation().sessionId())),
                toSse(ChatStreamEvent.step(
                        "evidence-gate",
                        MODE,
                        preparedSession.conversation().id(),
                        preparedSession.conversation().sessionId(),
                        "检索证据不足，已直接降级为安全答复：" + evidenceAssessment.reason(),
                        java.util.Map.of("stepName", STEP_NAME + "-evidence-gate")
                )),
                toSse(ChatStreamEvent.sources(
                        MODE,
                        preparedSession.conversation().id(),
                        preparedSession.conversation().sessionId(),
                        preparedSession.sources()
                )),
                toSse(ChatStreamEvent.done(
                        MODE,
                        preparedSession.conversation().id(),
                        preparedSession.conversation().sessionId(),
                        fallbackAnswer,
                        null
                ))
        );
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
        return safeAnswer == null || safeAnswer.isBlank() ? answer : safeAnswer;
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }

    private record PreparedStreamingSession(
            com.example.agentplatform.chat.domain.Conversation conversation,
            com.example.agentplatform.chat.domain.ChatMessage userMessage,
            String systemPrompt,
            List<ChatAskResponse.SourceItem> sources,
            List<RetrievedChunk> retrievedChunks
    ) {
    }

    private static final class StreamingState {

        private final com.example.agentplatform.chat.domain.Conversation conversation;
        private final com.example.agentplatform.chat.domain.ChatMessage userMessage;
        private final Instant startTime;
        private List<ChatAskResponse.SourceItem> sources;
        private List<RetrievedChunk> retrievedChunks;
        private final StringBuilder answer;
        private final String originalQuestion;
        private String requestId;
        private String modelName;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String lastDelta = "";
        private boolean sourcesLoaded;

        private StreamingState(
                com.example.agentplatform.chat.domain.Conversation conversation,
                com.example.agentplatform.chat.domain.ChatMessage userMessage,
                List<ChatAskResponse.SourceItem> sources,
                List<RetrievedChunk> retrievedChunks,
                String defaultModelName
        ) {
            this.conversation = conversation;
            this.userMessage = userMessage;
            this.sources = new ArrayList<>(sources);
            this.retrievedChunks = new ArrayList<>(retrievedChunks);
            this.startTime = Instant.now();
            this.answer = new StringBuilder();
            this.originalQuestion = userMessage.content();
            this.modelName = defaultModelName;
            this.sourcesLoaded = false;
        }

        private void capture(ChatResponse chatResponse) {
            this.lastDelta = "";
            if (chatResponse == null) {
                return;
            }
            String currentText = chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null
                    ? null
                    : chatResponse.getResult().getOutput().getText();
            String delta = normalizeDelta(currentText);
            if (!delta.isBlank()) {
                this.answer.append(delta);
                this.lastDelta = delta;
            }
            var metadata = chatResponse.getMetadata();
            if (metadata == null) {
                return;
            }
            if (metadata.getId() != null && !metadata.getId().isBlank()) {
                this.requestId = metadata.getId();
            }
            if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
                this.modelName = metadata.getModel();
            }
            if (metadata.getUsage() != null) {
                this.promptTokens = metadata.getUsage().getPromptTokens();
                this.completionTokens = metadata.getUsage().getCompletionTokens();
                this.totalTokens = metadata.getUsage().getTotalTokens();
            }
        }

        private String normalizeDelta(String currentText) {
            if (currentText == null || currentText.isBlank()) {
                return "";
            }
            String accumulated = answer.toString();
            if (!accumulated.isBlank() && currentText.startsWith(accumulated)) {
                return currentText.substring(accumulated.length());
            }
            return currentText;
        }

        private Long conversationId() {
            return conversation.id();
        }

        private String sessionId() {
            return conversation.sessionId();
        }

        private Long userId() {
            return conversation.userId();
        }

        private String answer() {
            return answer.toString();
        }

        private com.example.agentplatform.chat.domain.ChatMessage userMessage() {
            return userMessage;
        }

        private List<ChatAskResponse.SourceItem> sources() {
            return sources;
        }

        private void replaceSources(List<ChatAskResponse.SourceItem> sources) {
            this.sources = new ArrayList<>(sources);
        }

        private void replaceRetrievedChunks(List<RetrievedChunk> retrievedChunks) {
            this.retrievedChunks = new ArrayList<>(retrievedChunks);
        }

        private boolean sourcesLoaded() {
            return sourcesLoaded;
        }

        private void markSourcesLoaded() {
            this.sourcesLoaded = true;
        }

        private Instant startTime() {
            return startTime;
        }

        private String originalQuestion() {
            return originalQuestion;
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

        private String lastDelta() {
            return lastDelta;
        }

        private List<RetrievedChunk> retrievedChunks() {
            return retrievedChunks;
        }
    }
}
