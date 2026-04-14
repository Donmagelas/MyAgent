package com.example.agentplatform.chat.service;

import com.example.agentplatform.config.AiModelProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证自定义 ChatModel 适配器已经接入 Spring AI 官方 Observability。
 */
class DashScopeCompatibleSpringAiChatModelObservationTest {

    private DashScopeCompatibleChatCompletionClient chatCompletionClient;
    private DashScopeCompatibleSpringAiChatModel chatModel;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        chatCompletionClient = mock(DashScopeCompatibleChatCompletionClient.class);
        meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));

        chatModel = new DashScopeCompatibleSpringAiChatModel(
                chatCompletionClient,
                new AiModelProperties("qwen3.5-flash", "qwen3-vl-embedding", "qwen3-vl-rerank"),
                observationRegistry
        );
    }

    @Test
    void shouldPublishObservationForSynchronousCall() {
        when(chatCompletionClient.complete(anyList(), eq("qwen3.5-flash")))
                .thenReturn(new ChatCompletionClient.ChatCompletionResult(
                        "req-sync-1",
                        "qwen3.5-flash",
                        "同步回答",
                        12,
                        8,
                        20
                ));

        ChatResponse response = chatModel.call(new Prompt(List.of(new UserMessage("你好"))));

        assertThat(response.getMetadata()).isNotNull();
        Timer timer = meterRegistry.find(DefaultChatModelObservationConvention.DEFAULT_NAME).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void shouldPublishObservationForStreamingCallAfterFluxCompletes() {
        when(chatCompletionClient.stream(anyList(), eq("qwen3.5-flash")))
                .thenReturn(Flux.just(
                        new ChatCompletionStreamClient.ChatCompletionChunk(
                                "req-stream-1",
                                "qwen3.5-flash",
                                "流式",
                                null,
                                null,
                                null,
                                false
                        ),
                        new ChatCompletionStreamClient.ChatCompletionChunk(
                                "req-stream-1",
                                "qwen3.5-flash",
                                "回答",
                                16,
                                9,
                                25,
                                true
                        )
                ));

        List<ChatResponse> responses = chatModel.stream(new Prompt(List.of(new UserMessage("流式问题"))))
                .collectList()
                .block();

        assertThat(responses).hasSize(2);
        Timer timer = meterRegistry.find(DefaultChatModelObservationConvention.DEFAULT_NAME).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
