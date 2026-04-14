package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.config.AiModelProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * direct 与 grounded 两条流式聊天链路的共享支撑服务。
 * 负责会话持久化、增量内容拼装、usage 记录和 SSE 事件转换。
 */
@Service
public class ChatStreamingSupportService {

    private final ChatPersistenceService chatPersistenceService;
    private final ChatCompletionStreamClient chatCompletionStreamClient;
    private final ChatUsageService chatUsageService;
    private final AiModelProperties aiModelProperties;

    public ChatStreamingSupportService(
            ChatPersistenceService chatPersistenceService,
            ChatCompletionStreamClient chatCompletionStreamClient,
            ChatUsageService chatUsageService,
            AiModelProperties aiModelProperties
    ) {
        this.chatPersistenceService = chatPersistenceService;
        this.chatCompletionStreamClient = chatCompletionStreamClient;
        this.chatUsageService = chatUsageService;
        this.aiModelProperties = aiModelProperties;
    }

    /**
     * 流式输出一次聊天响应，并在结束后持久化最终助手消息。
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
            Long userId,
            ChatAskRequest request,
            String mode,
            String stepName,
            Function<Conversation, String> systemPromptBuilder,
            List<ChatAskResponse.SourceItem> sources
    ) {
        return Mono.fromCallable(() -> prepareSession(userId, request, systemPromptBuilder))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(preparedSession -> doStream(
                        request,
                        mode,
                        stepName,
                        preparedSession.systemPrompt(),
                        sources,
                        preparedSession.session()
                ));
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> doStream(
            ChatAskRequest request,
            String mode,
            String stepName,
            String systemPrompt,
            List<ChatAskResponse.SourceItem> sources,
            StreamingSession session
    ) {
        StreamingState state = new StreamingState(
                session.conversation(),
                session.userMessage(),
                mode,
                stepName,
                aiModelProperties.chatModel(),
                sources
        );

        Flux<ServerSentEvent<ChatStreamEvent>> prefix = Flux.fromIterable(buildPrefixEvents(state));
        Flux<ServerSentEvent<ChatStreamEvent>> deltas = chatCompletionStreamClient.stream(systemPrompt, request.message())
                .handle((chunk, sink) -> {
                    state.capture(chunk);
                    if (chunk.delta() != null && !chunk.delta().isBlank()) {
                        sink.next(toSse(ChatStreamEvent.delta(
                                state.mode,
                                state.conversation.id(),
                                state.conversation.sessionId(),
                                chunk.delta()
                        )));
                    }
                });

        Flux<ServerSentEvent<ChatStreamEvent>> successTail = Mono.fromCallable(() -> finalizeSuccess(state))
                .subscribeOn(Schedulers.boundedElastic())
                .flux();

        return Flux.concat(prefix, deltas, successTail)
                .onErrorResume(throwable -> Mono.fromCallable(() -> finalizeFailure(state, throwable))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flux());
    }

    private StreamingSession createSession(Long userId, ChatAskRequest request) {
        Conversation conversation = chatPersistenceService.getOrCreateConversation(userId, request);
        ChatMessage userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());
        return new StreamingSession(conversation, userMessage);
    }

    private PreparedStreamingSession prepareSession(
            Long userId,
            ChatAskRequest request,
            Function<Conversation, String> systemPromptBuilder
    ) {
        StreamingSession session = createSession(userId, request);
        return new PreparedStreamingSession(session, systemPromptBuilder.apply(session.conversation()));
    }

    private List<ServerSentEvent<ChatStreamEvent>> buildPrefixEvents(StreamingState state) {
        List<ServerSentEvent<ChatStreamEvent>> events = new ArrayList<>();
        events.add(toSse(ChatStreamEvent.start(
                state.mode,
                state.conversation.id(),
                state.conversation.sessionId()
        )));
        if (!state.sources.isEmpty()) {
            events.add(toSse(ChatStreamEvent.sources(
                    state.mode,
                    state.conversation.id(),
                    state.conversation.sessionId(),
                    state.sources
            )));
        }
        return events;
    }

    private ServerSentEvent<ChatStreamEvent> finalizeSuccess(StreamingState state) {
        ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                state.conversation.userId(),
                state.conversation.id(),
                state.answer.toString(),
                state.modelName
        );
        long latencyMs = Duration.between(state.startTime, Instant.now()).toMillis();
        ChatCompletionClient.ChatCompletionResult result = new ChatCompletionClient.ChatCompletionResult(
                state.requestId,
                state.modelName,
                state.answer.toString(),
                state.promptTokens,
                state.completionTokens,
                state.totalTokens
        );
        chatUsageService.save(
                state.conversation.id(),
                assistantMessage.id(),
                state.stepName,
                result,
                latencyMs,
                true,
                null
        );
        return toSse(ChatStreamEvent.done(
                state.mode,
                state.conversation.id(),
                state.conversation.sessionId(),
                state.answer.toString(),
                new ChatStreamEvent.Usage(
                        state.requestId,
                        state.modelName,
                        state.promptTokens,
                        state.completionTokens,
                        state.totalTokens,
                        latencyMs
                )
        ));
    }

    private ServerSentEvent<ChatStreamEvent> finalizeFailure(StreamingState state, Throwable throwable) {
        long latencyMs = Duration.between(state.startTime, Instant.now()).toMillis();
        chatUsageService.save(
                state.conversation.id(),
                state.userMessage.id(),
                state.stepName,
                null,
                latencyMs,
                false,
                throwable.getMessage()
        );
        return toSse(ChatStreamEvent.error(
                state.mode,
                state.conversation.id(),
                state.conversation.sessionId(),
                throwable.getMessage()
        ));
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }

    private record StreamingSession(
            Conversation conversation,
            ChatMessage userMessage
    ) {
    }

    private record PreparedStreamingSession(
            StreamingSession session,
            String systemPrompt
    ) {
    }

    private static final class StreamingState {

        private final Conversation conversation;
        private final ChatMessage userMessage;
        private final Instant startTime;
        private final String mode;
        private final String stepName;
        private final List<ChatAskResponse.SourceItem> sources;
        private final StringBuilder answer;
        private String requestId;
        private String modelName;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        private StreamingState(
                Conversation conversation,
                ChatMessage userMessage,
                String mode,
                String stepName,
                String defaultModelName,
                List<ChatAskResponse.SourceItem> sources
        ) {
            this.conversation = conversation;
            this.userMessage = userMessage;
            this.startTime = Instant.now();
            this.mode = mode;
            this.stepName = stepName;
            this.sources = sources;
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
    }
}
