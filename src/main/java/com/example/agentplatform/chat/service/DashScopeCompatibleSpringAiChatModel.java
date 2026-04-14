package com.example.agentplatform.chat.service;

import com.example.agentplatform.config.AiModelProperties;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * 基于百炼兼容接口的 Spring AI ChatModel 适配器。
 * 让上层继续使用 Spring AI 的 ChatClient、Advisor 和结构化输出能力，
 * 底层请求仍然走项目内已经验证可用的百炼兼容聊天客户端。
 */
@Primary
@Component
public class DashScopeCompatibleSpringAiChatModel implements ChatModel {

    private static final String OBSERVATION_PROVIDER = "dashscope-compatible";

    private final DashScopeCompatibleChatCompletionClient chatCompletionClient;
    private final AiModelProperties aiModelProperties;
    private final ObservationRegistry observationRegistry;
    private final ChatModelObservationConvention observationConvention;

    public DashScopeCompatibleSpringAiChatModel(
            DashScopeCompatibleChatCompletionClient chatCompletionClient,
            AiModelProperties aiModelProperties,
            ObservationRegistry observationRegistry
    ) {
        this.chatCompletionClient = chatCompletionClient;
        this.aiModelProperties = aiModelProperties;
        this.observationRegistry = observationRegistry;
        this.observationConvention = new DefaultChatModelObservationConvention();
    }

    /**
     * 执行一次 Spring AI 标准聊天调用，并补充官方 observation。
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        List<DashScopeCompatibleChatCompletionClient.RequestMessage> messages = toRequestMessages(prompt);
        String modelName = resolveModelName(prompt);
        ChatModelObservationContext observationContext = buildObservationContext(prompt);
        Observation observation = startObservation(observationContext);
        try {
            ChatCompletionClient.ChatCompletionResult result = chatCompletionClient.complete(messages, modelName, resolveTemperature(prompt));
            ChatResponse response = toChatResponse(result);
            observationContext.setResponse(response);
            return response;
        }
        catch (RuntimeException exception) {
            observation.error(exception);
            throw exception;
        }
        finally {
            observation.stop();
        }
    }

    /**
     * 执行一次 Spring AI 标准流式聊天调用，并在整个流完成后结束 observation。
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<DashScopeCompatibleChatCompletionClient.RequestMessage> messages = toRequestMessages(prompt);
        String modelName = resolveModelName(prompt);
        return Flux.defer(() -> {
            ChatModelObservationContext observationContext = buildObservationContext(prompt);
            Observation observation = startObservation(observationContext);
            return chatCompletionClient.stream(messages, modelName, resolveTemperature(prompt))
                    .map(this::toChatResponse)
                    .doOnNext(observationContext::setResponse)
                    .doOnError(observation::error)
                    .doFinally(signalType -> observation.stop());
        });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return new DefaultChatOptionsBuilder()
                .model(aiModelProperties.chatModel())
                .temperature(aiModelProperties.chatTemperature())
                .build();
    }

    private List<DashScopeCompatibleChatCompletionClient.RequestMessage> toRequestMessages(Prompt prompt) {
        return prompt.getInstructions().stream()
                .map(this::toRequestMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    private DashScopeCompatibleChatCompletionClient.RequestMessage toRequestMessage(Message message) {
        if (!(message instanceof AbstractMessage abstractMessage)) {
            return null;
        }
        String text = abstractMessage.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return new DashScopeCompatibleChatCompletionClient.RequestMessage(
                resolveRole(message.getMessageType()),
                text
        );
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

    private String resolveModelName(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options != null && options.getModel() != null && !options.getModel().isBlank()) {
            return options.getModel();
        }
        return aiModelProperties.chatModel();
    }

    /**
     * ??????????????
     */
    private Double resolveTemperature(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options != null && options.getTemperature() != null) {
            return options.getTemperature();
        }
        return aiModelProperties.chatTemperature();
    }

    /**
     * 构造 Spring AI 官方聊天观测上下文。
     */
    private ChatModelObservationContext buildObservationContext(Prompt prompt) {
        return ChatModelObservationContext.builder()
                .prompt(ensurePromptOptions(prompt))
                .provider(OBSERVATION_PROVIDER)
                .build();
    }

    /**
     * Spring AI 默认观测约定会直接读取 Prompt 的 options。
     * 这里补一个带默认模型名的安全 Prompt，避免 options 为 null。
     */
    private Prompt ensurePromptOptions(Prompt prompt) {
        if (prompt.getOptions() != null) {
            return prompt;
        }
        return new Prompt(prompt.getInstructions(), getDefaultOptions());
    }

    /**
     * 启动一次聊天模型观测，供同步和流式请求复用。
     */
    private Observation startObservation(ChatModelObservationContext observationContext) {
        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.start(
                observationConvention,
                observationConvention,
                () -> observationContext,
                observationRegistry
        );
    }

    private ChatResponse toChatResponse(ChatCompletionClient.ChatCompletionResult result) {
        AssistantMessage assistantMessage = new AssistantMessage(result.answer());
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation), buildMetadata(
                result.requestId(),
                result.modelName(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens()
        ));
    }

    private ChatResponse toChatResponse(ChatCompletionStreamClient.ChatCompletionChunk chunk) {
        AssistantMessage assistantMessage = new AssistantMessage(chunk.delta() == null ? "" : chunk.delta());
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation), buildMetadata(
                chunk.requestId(),
                chunk.modelName(),
                chunk.promptTokens(),
                chunk.completionTokens(),
                chunk.totalTokens()
        ));
    }

    private ChatResponseMetadata buildMetadata(
            String requestId,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                .id(requestId)
                .model(modelName);
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            builder.usage(new SimpleUsage(promptTokens, completionTokens, totalTokens));
        }
        return builder.build();
    }

    /**
     * 轻量适配 Spring AI usage 元数据接口。
     */
    private record SimpleUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Integer getTotalTokens() {
            return totalTokens;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
