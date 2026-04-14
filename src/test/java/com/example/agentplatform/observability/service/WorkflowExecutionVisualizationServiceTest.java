package com.example.agentplatform.observability.service;

import com.example.agentplatform.config.ObservabilityProperties;
import com.example.agentplatform.observability.dto.ModelUsageLogEntry;
import com.example.agentplatform.observability.dto.WorkflowExecutionView;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import com.example.agentplatform.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作流执行可视化聚合服务测试。
 */
class WorkflowExecutionVisualizationServiceTest {

    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final ModelUsageLogService modelUsageLogService = mock(ModelUsageLogService.class);
    private final WorkflowExecutionVisualizationService service = new WorkflowExecutionVisualizationService(
            workflowService,
            modelUsageLogService,
            new ObservabilityProperties(10)
    );

    @Test
    void shouldAggregateWorkflowTasksAndUsage() {
        OffsetDateTime now = OffsetDateTime.now();
        WorkflowRecord workflow = new WorkflowRecord(
                101L,
                1L,
                "Agent Workflow",
                "测试工作流",
                WorkflowStatus.RUNNING,
                Map.of("conversationId", 201L, "sessionId", "sess-1"),
                Map.of(),
                null,
                true,
                Map.of("mode", "REACT"),
                now.minusMinutes(2),
                now.minusMinutes(1),
                now.minusMinutes(1),
                null
        );
        TaskRecord rootTask = new TaskRecord(
                1L,
                101L,
                null,
                1L,
                "agent-run-root",
                "Agent 主任务",
                "根任务",
                "AGENT_RUN",
                TaskStatus.RUNNING,
                0,
                Map.of(),
                Map.of(),
                null,
                0,
                0,
                false,
                "agent",
                "REACT",
                Map.of(),
                List.of(),
                now.minusMinutes(2),
                now.minusMinutes(1),
                now.minusMinutes(1),
                null
        );
        TaskRecord planTask = new TaskRecord(
                2L,
                101L,
                1L,
                1L,
                "reason-1",
                "推理步骤 1",
                "推理",
                "AGENT_REASON",
                TaskStatus.COMPLETED,
                100,
                Map.of(),
                Map.of("thought", "先检索"),
                null,
                0,
                0,
                false,
                "agent",
                "REACT",
                Map.of(),
                List.of(),
                now.minusMinutes(2),
                now.minusSeconds(30),
                now.minusSeconds(30),
                now.minusSeconds(20)
        );
        TaskRecord toolTask = new TaskRecord(
                3L,
                101L,
                1L,
                1L,
                "tool-1-search",
                "工具执行 1",
                "工具",
                "AGENT_TOOL",
                TaskStatus.RUNNING,
                50,
                Map.of(),
                Map.of(),
                null,
                0,
                0,
                false,
                "agent",
                "REACT",
                Map.of(),
                List.of(),
                now.minusMinutes(1),
                now.minusSeconds(10),
                now.minusSeconds(10),
                now
        );
        List<ModelUsageLogEntry> usageLogs = List.of(
                new ModelUsageLogEntry(
                        11L,
                        101L,
                        null,
                        201L,
                        301L,
                        "req-1",
                        "agent-task-plan",
                        "qwen3.5-flash",
                        "dashscope",
                        10,
                        20,
                        30,
                        1000L,
                        true,
                        null,
                        now.minusSeconds(50)
                ),
                new ModelUsageLogEntry(
                        12L,
                        101L,
                        null,
                        201L,
                        301L,
                        "req-2",
                        "agent-loop-plan-1",
                        "qwen3.5-flash",
                        "dashscope",
                        15,
                        25,
                        40,
                        1500L,
                        true,
                        null,
                        now.minusSeconds(30)
                )
        );

        when(workflowService.get(1L, 101L)).thenReturn(workflow);
        when(workflowService.listTasks(1L, 101L)).thenReturn(List.of(rootTask, planTask, toolTask));
        when(modelUsageLogService.findByWorkflowId(101L)).thenReturn(usageLogs);

        WorkflowExecutionView view = service.getWorkflowExecution(1L, 101L);

        assertThat(view.workflowId()).isEqualTo(101L);
        assertThat(view.conversationId()).isEqualTo(201L);
        assertThat(view.sessionId()).isEqualTo("sess-1");
        assertThat(view.currentStep()).isNotNull();
        assertThat(view.currentStep().taskId()).isEqualTo(3L);
        assertThat(view.currentStep().taskType()).isEqualTo("AGENT_TOOL");
        assertThat(view.taskStatusCounts()).containsEntry("RUNNING", 2L);
        assertThat(view.usage().callCount()).isEqualTo(2L);
        assertThat(view.usage().promptTokens()).isEqualTo(25);
        assertThat(view.usage().completionTokens()).isEqualTo(45);
        assertThat(view.usage().totalTokens()).isEqualTo(70);
        assertThat(view.usage().byStep()).hasSize(2);
        assertThat(view.tasks()).hasSize(3);
    }
}
