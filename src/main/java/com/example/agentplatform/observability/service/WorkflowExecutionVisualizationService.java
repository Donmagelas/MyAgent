package com.example.agentplatform.observability.service;

import com.example.agentplatform.config.ObservabilityProperties;
import com.example.agentplatform.observability.dto.ModelUsageLogEntry;
import com.example.agentplatform.observability.dto.UsageStepSummaryView;
import com.example.agentplatform.observability.dto.UsageSummaryView;
import com.example.agentplatform.observability.dto.WorkflowCurrentStepView;
import com.example.agentplatform.observability.dto.WorkflowExecutionView;
import com.example.agentplatform.observability.dto.WorkflowTaskView;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工作流执行可视化聚合服务。
 * 把 workflow、tasks 与 usage 聚合成前端可以直接消费的执行视图。
 */
@Service
public class WorkflowExecutionVisualizationService {

    private final WorkflowService workflowService;
    private final ModelUsageLogService modelUsageLogService;
    private final ObservabilityProperties observabilityProperties;

    public WorkflowExecutionVisualizationService(
            WorkflowService workflowService,
            ModelUsageLogService modelUsageLogService,
            ObservabilityProperties observabilityProperties
    ) {
        this.workflowService = workflowService;
        this.modelUsageLogService = modelUsageLogService;
        this.observabilityProperties = observabilityProperties;
    }

    /**
     * 查询某个工作流的执行可视化视图。
     */
    public WorkflowExecutionView getWorkflowExecution(Long userId, Long workflowId) {
        WorkflowRecord workflow = workflowService.get(userId, workflowId);
        List<TaskRecord> tasks = workflowService.listTasks(userId, workflowId);
        List<ModelUsageLogEntry> usageLogs = modelUsageLogService.findByWorkflowId(workflowId);
        return new WorkflowExecutionView(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                workflow.status(),
                extractLong(workflow.input(), "conversationId"),
                extractString(workflow.input(), "sessionId"),
                workflow.createdAt(),
                workflow.startedAt(),
                workflow.completedAt(),
                workflow.input(),
                workflow.result(),
                workflow.errorMessage(),
                workflow.metadata(),
                summarizeTaskStatuses(tasks),
                resolveCurrentStep(tasks),
                summarizeUsage(usageLogs),
                tasks.stream().map(this::toTaskView).toList()
        );
    }

    private WorkflowTaskView toTaskView(TaskRecord task) {
        return new WorkflowTaskView(
                task.id(),
                task.parentTaskId(),
                task.clientTaskKey(),
                task.name(),
                task.description(),
                task.taskType(),
                task.status(),
                task.progress(),
                task.sourceType(),
                task.sourceRef(),
                task.input(),
                task.result(),
                task.errorMessage(),
                task.metadata(),
                task.blockedByTaskIds(),
                task.startedAt(),
                task.completedAt(),
                task.updatedAt()
        );
    }

    private Map<String, Long> summarizeTaskStatuses(List<TaskRecord> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(task -> task.status().name(), LinkedHashMap::new, Collectors.counting()));
    }

    private WorkflowCurrentStepView resolveCurrentStep(List<TaskRecord> tasks) {
        Optional<TaskRecord> runningTask = tasks.stream()
                .filter(task -> task.status() == TaskStatus.RUNNING)
                .filter(task -> !isRootTask(task))
                .max(Comparator.comparing(TaskRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (runningTask.isPresent()) {
            return toCurrentStepView(runningTask.get());
        }

        Optional<TaskRecord> latestActiveTask = tasks.stream()
                .filter(task -> !task.status().isTerminal())
                .filter(task -> !isRootTask(task))
                .max(Comparator.comparing(TaskRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        if (latestActiveTask.isPresent()) {
            return toCurrentStepView(latestActiveTask.get());
        }

        return tasks.stream()
                .filter(task -> !isRootTask(task))
                .max(Comparator.comparing(TaskRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toCurrentStepView)
                .orElse(null);
    }

    private WorkflowCurrentStepView toCurrentStepView(TaskRecord task) {
        return new WorkflowCurrentStepView(
                task.id(),
                task.name(),
                task.taskType(),
                task.status(),
                task.progress(),
                task.updatedAt()
        );
    }

    private UsageSummaryView summarizeUsage(List<ModelUsageLogEntry> usageLogs) {
        int promptTokens = usageLogs.stream().map(ModelUsageLogEntry::promptTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int completionTokens = usageLogs.stream().map(ModelUsageLogEntry::completionTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalTokens = usageLogs.stream().map(ModelUsageLogEntry::totalTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        long totalLatencyMs = usageLogs.stream().map(ModelUsageLogEntry::latencyMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long successCount = usageLogs.stream().filter(ModelUsageLogEntry::success).count();
        long failureCount = usageLogs.size() - successCount;

        List<UsageStepSummaryView> byStep = usageLogs.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.stepName() + "|" + entry.modelName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(entries -> {
                    ModelUsageLogEntry first = entries.get(0);
                    return new UsageStepSummaryView(
                            first.stepName(),
                            first.modelName(),
                            entries.size(),
                            entries.stream().filter(ModelUsageLogEntry::success).count(),
                            entries.stream().filter(entry -> !entry.success()).count(),
                            entries.stream().map(ModelUsageLogEntry::promptTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                            entries.stream().map(ModelUsageLogEntry::completionTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                            entries.stream().map(ModelUsageLogEntry::totalTokens).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                            entries.stream().map(ModelUsageLogEntry::latencyMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum()
                    );
                })
                .toList();

        List<ModelUsageLogEntry> recentLogs = usageLogs.stream()
                .sorted(Comparator.comparing(ModelUsageLogEntry::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(observabilityProperties.usageDetailLimit(), 1))
                .toList();

        return new UsageSummaryView(
                usageLogs.size(),
                successCount,
                failureCount,
                promptTokens,
                completionTokens,
                totalTokens,
                totalLatencyMs,
                byStep,
                recentLogs
        );
    }

    private boolean isRootTask(TaskRecord task) {
        return "AGENT_RUN".equals(task.taskType()) || "SUBAGENT_RUN".equals(task.taskType());
    }

    private Long extractLong(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractString(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
