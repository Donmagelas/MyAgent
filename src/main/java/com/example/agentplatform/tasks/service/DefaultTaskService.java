package com.example.agentplatform.tasks.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.domain.TaskStatus;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;
import com.example.agentplatform.tasks.repository.TaskDependencyRepository;
import com.example.agentplatform.tasks.repository.TaskRepository;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.domain.WorkflowStatus;
import com.example.agentplatform.workflow.repository.WorkflowRepository;
import com.example.agentplatform.workflow.service.WorkflowStatusResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 默认任务服务实现。
 * 负责任务创建、状态推进、依赖解锁以及工作流状态同步。
 */
@Service
public class DefaultTaskService implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusResolver workflowStatusResolver;

    public DefaultTaskService(
            TaskRepository taskRepository,
            TaskDependencyRepository taskDependencyRepository,
            WorkflowRepository workflowRepository,
            WorkflowStatusResolver workflowStatusResolver
    ) {
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStatusResolver = workflowStatusResolver;
    }

    @Override
    @Transactional
    public TaskRecord create(Long userId, TaskCreateRequest request) {
        TaskStatus initialStatus = request.blockedByTaskIds() == null || request.blockedByTaskIds().isEmpty()
                ? TaskStatus.READY
                : TaskStatus.BLOCKED;
        TaskRecord task = taskRepository.save(userId, request, initialStatus);
        syncWorkflowStatus(userId, task.workflowId());
        return task;
    }

    @Override
    public TaskRecord get(Long userId, Long taskId) {
        return taskRepository.findById(userId, taskId)
                .orElseThrow(() -> new ApplicationException("任务不存在或无权访问"));
    }

    @Override
    public List<TaskRecord> listByWorkflow(Long userId, Long workflowId) {
        return taskRepository.findByWorkflowId(userId, workflowId);
    }

    @Override
    @Transactional
    public TaskRecord updateStatus(Long userId, Long taskId, TaskStatusUpdateRequest request) {
        TaskRecord updatedTask = taskRepository.updateStatus(userId, taskId, request);
        if (request.status() == TaskStatus.COMPLETED) {
            unlockDependents(updatedTask.id());
        }
        syncWorkflowStatus(userId, updatedTask.workflowId(), request);
        return get(userId, taskId);
    }

    @Override
    @Transactional
    public TaskRecord requestCancel(Long userId, Long taskId) {
        TaskRecord task = taskRepository.requestCancel(userId, taskId);
        if (task.status().isTerminal()) {
            return task;
        }
        TaskRecord canceledTask = taskRepository.updateStatus(
                userId,
                taskId,
                new TaskStatusUpdateRequest(TaskStatus.CANCELED, task.progress(), task.result(), "任务已取消")
        );
        syncWorkflowStatus(userId, canceledTask.workflowId());
        return canceledTask;
    }

    private void unlockDependents(Long taskId) {
        for (Long dependentTaskId : taskDependencyRepository.findDependentTaskIds(taskId)) {
            if (taskDependencyRepository.countIncompleteDependencies(dependentTaskId) == 0) {
                taskRepository.markReady(dependentTaskId);
            }
        }
    }

    private void syncWorkflowStatus(Long userId, Long workflowId) {
        syncWorkflowStatus(userId, workflowId, null);
    }

    private void syncWorkflowStatus(Long userId, Long workflowId, TaskStatusUpdateRequest request) {
        if (workflowId == null) {
            return;
        }
        WorkflowRecord workflow = workflowRepository.findById(userId, workflowId)
                .orElseThrow(() -> new ApplicationException("工作流不存在或无权访问"));
        List<TaskRecord> tasks = taskRepository.findByWorkflowId(userId, workflowId);
        WorkflowStatus workflowStatus = workflowStatusResolver.resolve(tasks);
        Map<String, Object> result = workflow.result();
        String errorMessage = workflow.errorMessage();
        if (request != null && request.status() == TaskStatus.COMPLETED) {
            Long lastCompletedTaskId = tasks.stream()
                    .filter(task -> task.status() == TaskStatus.COMPLETED)
                    .map(TaskRecord::id)
                    .max(Long::compareTo)
                    .orElse(null);
            result = lastCompletedTaskId == null ? Map.of() : Map.of("lastCompletedTaskId", lastCompletedTaskId);
        }
        if (request != null && request.status() == TaskStatus.FAILED) {
            errorMessage = request.errorMessage();
        }
        workflowRepository.updateStatus(userId, workflowId, workflowStatus, result, errorMessage);
    }
}
