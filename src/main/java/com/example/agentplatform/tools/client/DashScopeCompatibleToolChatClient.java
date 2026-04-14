package com.example.agentplatform.tools.client;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AiClientProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 百炼兼容工具聊天客户端。
 * 负责发送包含工具定义的聊天请求，并把工具调用结果解析成 Spring AI ChatResponse。
 */
@Component
public class DashScopeCompatibleToolChatClient {

    private static final String CHAT_COMPLETIONS_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiModelProperties aiModelProperties;
    private final String dashScopeApiKey;

    public DashScopeCompatibleToolChatClient(
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

    /**
     * 发送一次带工具定义的聊天请求。
     */
    public ChatResponse call(Prompt prompt, List<ToolDefinition> toolDefinitions) {
        ToolChatCompletionRequest request = new ToolChatCompletionRequest(
                resolveModelName(prompt),
                toMessagePayloads(prompt.getInstructions()),
                toToolPayloads(toolDefinitions),
                resolveTemperature(prompt),
                Boolean.FALSE
        );

        String response = webClient.post()
                .uri(CHAT_COMPLETIONS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(120));

        if (response == null || response.isBlank()) {
            throw new ApplicationException("Tool chat response is empty");
        }
        return parseResponse(response);
    }

    private String resolveApiKey() {
        if (dashScopeApiKey == null || dashScopeApiKey.isBlank()) {
            throw new ApplicationException("DashScope API key is missing");
        }
        return dashScopeApiKey;
    }

    private String resolveModelName(Prompt prompt) {
        if (prompt.getOptions() != null && prompt.getOptions().getModel() != null && !prompt.getOptions().getModel().isBlank()) {
            return prompt.getOptions().getModel();
        }
        return aiModelProperties.chatModel();
    }

    /**
     * ??????????????
     */
    private Double resolveTemperature(Prompt prompt) {
        if (prompt.getOptions() != null && prompt.getOptions().getTemperature() != null) {
            return prompt.getOptions().getTemperature();
        }
        return aiModelProperties.chatTemperature();
    }

    private List<MessagePayload> toMessagePayloads(List<Message> messages) {
        List<MessagePayload> payloads = new ArrayList<>();
        for (Message message : messages) {
            payloads.addAll(toMessagePayloads(message));
        }
        if (payloads.isEmpty()) {
            throw new ApplicationException("Tool chat messages must not be empty");
        }
        return payloads;
    }

    private List<MessagePayload> toMessagePayloads(Message message) {
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return toolResponseMessage.getResponses().stream()
                    .map(response -> new MessagePayload(
                            "tool",
                            response.responseData(),
                            response.name(),
                            response.id(),
                            null
                    ))
                    .toList();
        }
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            List<ToolCallPayload> toolCalls = assistantMessage.getToolCalls().stream()
                    .map(toolCall -> new ToolCallPayload(
                            toolCall.id(),
                            toolCall.type(),
                            new FunctionPayload(toolCall.name(), toolCall.arguments())
                    ))
                    .toList();
            return List.of(new MessagePayload(
                    "assistant",
                    assistantMessage.getText(),
                    null,
                    null,
                    toolCalls
            ));
        }
        if (message instanceof AbstractMessage abstractMessage) {
            return List.of(new MessagePayload(
                    resolveRole(abstractMessage.getMessageType()),
                    abstractMessage.getText(),
                    null,
                    null,
                    null
            ));
        }
        return List.of();
    }

    private String resolveRole(MessageType messageType) {
        if (messageType == null) {
            return "user";
        }
        return switch (messageType) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case USER -> "user";
        };
    }

    private List<ToolPayload> toToolPayloads(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream()
                .map(this::toToolPayload)
                .toList();
    }

    private ToolPayload toToolPayload(ToolDefinition toolDefinition) {
        try {
            JsonNode schema = objectMapper.readTree(toolDefinition.inputSchema());
            return new ToolPayload(
                    "function",
                    new FunctionDefinitionPayload(
                            toolDefinition.name(),
                            toolDefinition.description(),
                            schema
                    )
            );
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to parse tool input schema: " + toolDefinition.name(), exception);
        }
    }

    private ChatResponse parseResponse(String responseBody) {
        try {
            ToolChatCompletionResponse response = objectMapper.readValue(responseBody, ToolChatCompletionResponse.class);
            ChoicePayload choice = response.choices() == null || response.choices().isEmpty()
                    ? null
                    : response.choices().get(0);
            if (choice == null || choice.message() == null) {
                throw new ApplicationException("Tool chat choice is missing");
            }

            List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() == null
                    ? List.of()
                    : choice.message().toolCalls().stream()
                    .map(toolCall -> new AssistantMessage.ToolCall(
                            toolCall.id(),
                            toolCall.type(),
                            toolCall.function().name(),
                            toolCall.function().arguments()
                    ))
                    .toList();

            AssistantMessage assistantMessage = new AssistantMessage(
                    choice.message().content() == null ? "" : choice.message().content(),
                    java.util.Map.of(),
                    toolCalls
            );
            Generation generation = new Generation(
                    assistantMessage,
                    ChatGenerationMetadata.builder()
                            .finishReason(choice.finishReason())
                            .build()
            );
            return new ChatResponse(
                    List.of(generation),
                    ChatResponseMetadata.builder()
                            .id(response.id())
                            .model(response.model())
                            .usage(toUsage(response.usage()))
                            .build()
            );
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("Failed to parse tool chat response", exception);
        }
    }

    private Usage toUsage(UsagePayload usagePayload) {
        if (usagePayload == null) {
            return null;
        }
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return usagePayload.promptTokens();
            }

            @Override
            public Integer getCompletionTokens() {
                return usagePayload.completionTokens();
            }

            @Override
            public Integer getTotalTokens() {
                return usagePayload.totalTokens();
            }

            @Override
            public Object getNativeUsage() {
                return usagePayload;
            }
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ToolChatCompletionRequest(
            String model,
            List<MessagePayload> messages,
            List<ToolPayload> tools,
            Double temperature,
            @JsonProperty("enable_thinking") Boolean enableThinking
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MessagePayload(
            String role,
            String content,
            String name,
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("tool_calls") List<ToolCallPayload> toolCalls
    ) {
    }

    private record ToolPayload(
            String type,
            FunctionDefinitionPayload function
    ) {
    }

    private record FunctionDefinitionPayload(
            String name,
            String description,
            JsonNode parameters
    ) {
    }

    private record ToolChatCompletionResponse(
            String id,
            String model,
            List<ChoicePayload> choices,
            UsagePayload usage
    ) {
    }

    private record ChoicePayload(
            int index,
            MessagePayload message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record ToolCallPayload(
            String id,
            String type,
            FunctionPayload function
    ) {
    }

    private record FunctionPayload(
            String name,
            String arguments
    ) {
    }

    private record UsagePayload(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
