package com.example.agentplatform.chat.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AiClientProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DashScope OpenAI 兼容聊天客户端。
 * 在 ChatCompletionClient 接口后封装提供方专有的 HTTP 协议细节。
 */
@Service
public class DashScopeCompatibleChatCompletionClient implements ChatCompletionClient, ChatCompletionStreamClient {

    private static final String CHAT_COMPLETIONS_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiModelProperties aiModelProperties;
    private final String dashScopeApiKey;

    public DashScopeCompatibleChatCompletionClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AiModelProperties aiModelProperties,
            AiClientProperties aiClientProperties,
            @Value("${spring.ai.dashscope.api-key:}") String dashScopeApiKey
    ) {
        this.webClient = webClientBuilder
                .clone()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .option(
                                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                        Math.toIntExact(aiClientProperties.connectTimeout().toMillis())
                                )
                                .responseTimeout(aiClientProperties.responseTimeout())
                ))
                .build();
        this.objectMapper = objectMapper;
        this.aiModelProperties = aiModelProperties;
        this.dashScopeApiKey = dashScopeApiKey;
    }

    /** 把流式响应聚合成一次完整的非流式结果。 */
    @Override
    public ChatCompletionResult complete(String systemPrompt, String userMessage, Double temperature) {
        return complete(List.of(
                new RequestMessage("system", systemPrompt),
                new RequestMessage("user", userMessage)
        ), aiModelProperties.chatModel(), temperature);
    }

    public ChatCompletionResult complete(List<RequestMessage> messages, String modelName, Double temperature) {
        List<ChatCompletionChunk> chunks = stream(messages, modelName, temperature)
                .collectList()
                .block(Duration.ofSeconds(120));

        if (chunks == null || chunks.isEmpty()) {
            throw new ApplicationException("Empty chat completion response");
        }

        StringBuilder answer = new StringBuilder();
        String requestId = null;
        String resolvedModelName = resolveModelName(modelName);
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;

        for (ChatCompletionChunk chunk : chunks) {
            if (chunk.requestId() != null && !chunk.requestId().isBlank()) {
                requestId = chunk.requestId();
            }
            if (chunk.modelName() != null && !chunk.modelName().isBlank()) {
                resolvedModelName = chunk.modelName();
            }
            if (chunk.delta() != null && !chunk.delta().isBlank()) {
                answer.append(chunk.delta());
            }
            if (chunk.promptTokens() != null) {
                promptTokens = chunk.promptTokens();
            }
            if (chunk.completionTokens() != null) {
                completionTokens = chunk.completionTokens();
            }
            if (chunk.totalTokens() != null) {
                totalTokens = chunk.totalTokens();
            }
        }

        if (answer.isEmpty()) {
            throw new ApplicationException("Empty chat completion response");
        }

        return new ChatCompletionResult(
                requestId,
                resolvedModelName,
                answer.toString(),
                promptTokens,
                completionTokens,
                totalTokens
        );
    }

    /** 发送一次流式聊天补全请求。 */
    @Override
    public Flux<ChatCompletionChunk> stream(String systemPrompt, String userMessage, Double temperature) {
        return stream(List.of(
                new RequestMessage("system", systemPrompt),
                new RequestMessage("user", userMessage)
        ), aiModelProperties.chatModel(), temperature);
    }

    public Flux<ChatCompletionChunk> stream(List<RequestMessage> messages, String modelName, Double temperature) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                resolveModelName(modelName),
                toPayloadMessages(messages),
                resolveTemperature(temperature),
                Boolean.FALSE,
                Boolean.TRUE,
                new StreamOptions(Boolean.TRUE)
        );

        return webClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .handle((event, sink) -> {
                    String data = event.data();
                    if (data != null && !data.isBlank()) {
                        sink.next(data);
                    }
                })
                .cast(String.class)
                .takeUntil("[DONE]"::equals)
                .filter(data -> !"[DONE]".equals(data))
                .flatMapIterable(this::parseStreamChunks);
    }

    private String resolveModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName;
        }
        return aiModelProperties.chatModel();
    }

    /**
     * ??????????????
     */
    private Double resolveTemperature(Double temperature) {
        return temperature != null ? temperature : aiModelProperties.chatTemperature();
    }

    private String resolveApiKey() {
        if (dashScopeApiKey == null || dashScopeApiKey.isBlank()) {
            throw new ApplicationException("DashScope API key is missing");
        }
        return dashScopeApiKey;
    }

    private List<ChatMessagePayload> toPayloadMessages(List<RequestMessage> messages) {
        List<ChatMessagePayload> payloads = messages == null ? List.of() : messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .map(message -> new ChatMessagePayload(message.role(), message.content()))
                .toList();
        if (payloads.isEmpty()) {
            throw new ApplicationException("Chat completion messages must not be empty");
        }
        return payloads;
    }

    private List<ChatCompletionChunk> parseStreamChunks(String payload) {
        try {
            ChatCompletionResponse response = objectMapper.readValue(payload, ChatCompletionResponse.class);
            List<ChatCompletionChunk> chunks = new ArrayList<>();
            if (response.choices() != null) {
                for (ChoicePayload choice : response.choices()) {
                    if (choice.delta() != null
                            && choice.delta().content() != null
                            && !choice.delta().content().isBlank()) {
                        chunks.add(new ChatCompletionChunk(
                                response.id(),
                                response.model(),
                                choice.delta().content(),
                                null,
                                null,
                                null,
                                false
                        ));
                    }
                }
            }

            UsagePayload usage = response.usage();
            if (usage != null) {
                chunks.add(new ChatCompletionChunk(
                        response.id(),
                        response.model(),
                        null,
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        true
                ));
            }
            return chunks;
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to parse streaming chat response", exception);
        }
    }

    /** 提供方请求载荷。 */
    private record ChatCompletionRequest(
            String model,
            List<ChatMessagePayload> messages,
            Double temperature,
            @JsonProperty("enable_thinking") Boolean enableThinking,
            Boolean stream,
            @JsonProperty("stream_options") StreamOptions streamOptions
    ) {
    }

    private record StreamOptions(
            @JsonProperty("include_usage") Boolean includeUsage
    ) {
    }

    /** 提供方消息载荷。 */
    private record ChatMessagePayload(
            String role,
            String content
    ) {
    }

    /** 提供方响应载荷。 */
    private record ChatCompletionResponse(
            String id,
            String model,
            List<ChoicePayload> choices,
            UsagePayload usage
    ) {
    }

    private record ChoicePayload(
            Integer index,
            ChatMessagePayload message,
            DeltaPayload delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record DeltaPayload(
            String role,
            String content
    ) {
    }

    private record UsagePayload(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }

    public record RequestMessage(
            String role,
            String content
    ) {
    }
}
