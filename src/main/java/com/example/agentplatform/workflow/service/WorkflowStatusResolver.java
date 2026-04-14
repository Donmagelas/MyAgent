package com.example.agentplatform.workflow.service;

import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流状态汇总器。
 * 根据任务图当前状态推导工作流的聚合状态。
 */
@Component
public class WorkflowStatusResolver {

    /** 根据任务列表解析工作流状态。 */
    public WorkflowStatus resolve(List<TaskRecord> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return WorkflowStatus.PENDING;
        }
        boolean allCompleted = tasks.stream().allMatch(task -> task.status() == TaskStatus.COMPLETED);
        if (allCompleted) {
            return WorkflowStatus.COMPLETED;
        }
        boolean anyFailed = tasks.stream().anyMatch(task -> task.status() == TaskStatus.FAILED);
        if (anyFailed) {
            return WorkflowStatus.FAILED;
        }
        boolean anyRunning = tasks.stream().anyMatch(task -> task.status() == TaskStatus.RUNNING);
        if (anyRunning) {
            return WorkflowStatus.RUNNING;
        }
        boolean anyProgressed = tasks.stream().anyMatch(task ->
                task.status() == TaskStatus.COMPLETED
                        || task.progress() > 0
                        || task.startedAt() != null
        );
        if (anyProgressed) {
            return WorkflowStatus.RUNNING;
        }
        boolean allCanceled = tasks.stream().allMatch(task -> task.status() == TaskStatus.CANCELED);
        if (allCanceled) {
            return WorkflowStatus.CANCELED;
        }
        return WorkflowStatus.PENDING;
    }
}
