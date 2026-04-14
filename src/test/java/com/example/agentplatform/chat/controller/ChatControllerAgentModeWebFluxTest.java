package com.example.agentplatform.chat.controller;

import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.chat.service.SecuredChatFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ChatController 主链流式接口 WebFlux 测试。
 * 验证 /api/chat/stream 仍能走统一 Agent 流式入口。
 */
@WebFluxTest(
        controllers = ChatController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class
        }
)
class ChatControllerAgentModeWebFluxTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SecuredChatFacade securedChatFacade;

    @Test
    void shouldSupportUnifiedAgentStreamOnChatStream() {
        when(securedChatFacade.smartStream(any(), any())).thenReturn(Flux.just(
                toSse(ChatStreamEvent.start("agent-react", 301L, "chat-agent-stream", Map.of("workflowId", 401L))),
                toSse(ChatStreamEvent.step("plan", "agent-react", 301L, "chat-agent-stream", "第 1 步推理：调用 generate_pdf", Map.of("step", 1, "toolName", "generate_pdf"))),
                toSse(ChatStreamEvent.done("agent-react", 301L, "chat-agent-stream", "PDF generated", null))
        ));

        FluxExchangeResult<ChatStreamEvent> result = webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("""
                        {
                          "sessionId": "chat-agent-stream",
                          "message": "请使用 generate_pdf 工具生成 PDF"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(ChatStreamEvent.class);

        List<ChatStreamEvent> events = result.getResponseBody().collectList().block();
        org.assertj.core.api.Assertions.assertThat(events)
                .isNotNull()
                .hasSize(3);
        org.assertj.core.api.Assertions.assertThat(events.get(0).metadata()).containsEntry("workflowId", 401);
        org.assertj.core.api.Assertions.assertThat(events.get(1).type()).isEqualTo("plan");
        org.assertj.core.api.Assertions.assertThat(events.get(2).type()).isEqualTo("done");
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }
}
