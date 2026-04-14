package com.example.agentplatform.observability.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.observability.dto.UsageSummaryView;
import com.example.agentplatform.observability.dto.WorkflowCurrentStepView;
import com.example.agentplatform.observability.dto.WorkflowExecutionView;
import com.example.agentplatform.observability.service.WorkflowExecutionVisualizationService;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * 执行可视化控制器 WebFlux 测试。
 */
@WebFluxTest(
        controllers = ObservabilityController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
                ReactiveUserDetailsServiceAutoConfiguration.class
        }
)
class ObservabilityControllerWebFluxTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticatedUserAccessor authenticatedUserAccessor;

    @MockBean
    private WorkflowExecutionVisualizationService workflowExecutionVisualizationService;

    @Test
    void shouldReturnWorkflowExecutionView() {
        when(authenticatedUserAccessor.requireUserId(nullable(Authentication.class))).thenReturn(1L);
        when(workflowExecutionVisualizationService.getWorkflowExecution(1L, 101L)).thenReturn(new WorkflowExecutionView(
                101L,
                "Agent Workflow",
                "测试",
                WorkflowStatus.RUNNING,
                201L,
                "sess-1",
                OffsetDateTime.now().minusMinutes(2),
                OffsetDateTime.now().minusMinutes(1),
                null,
                Map.of("conversationId", 201L),
                Map.of(),
                null,
                Map.of("mode", "REACT"),
                Map.of("RUNNING", 1L, "COMPLETED", 2L),
                new WorkflowCurrentStepView(3L, "工具执行 1", "AGENT_TOOL", TaskStatus.RUNNING, 50, OffsetDateTime.now()),
                new UsageSummaryView(2L, 2L, 0L, 25, 45, 70, 2500L, List.of(), List.of()),
                List.of()
        ));

        webTestClient.get()
                .uri("/api/observability/workflows/101")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.workflowId").isEqualTo(101)
                .jsonPath("$.conversationId").isEqualTo(201)
                .jsonPath("$.currentStep.taskType").isEqualTo("AGENT_TOOL")
                .jsonPath("$.usage.totalTokens").isEqualTo(70);
    }
}
