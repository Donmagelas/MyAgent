package com.example.agentplatform.workflow.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.service.TaskService;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;
import com.example.agentplatform.workflow.dto.WorkflowTaskCreateRequest;
import com.example.agentplatform.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认工作流服务实现。
 * 负责先创建工作流实例，再按任务图批量落任务。
 */
@Service
public class DefaultWorkflowService implements WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final TaskService taskService;

    public DefaultWorkflowService(
            WorkflowRepository workflowRepository,
            TaskService taskService
    ) {
        this.workflowRepository = workflowRepository;
        this.taskService = taskService;
    }

    @Override
    @Transactional
    public WorkflowRecord create(Long userId, WorkflowCreateRequest request) {
        WorkflowRecord workflow = workflowRepository.save(userId, request);
        Map<String, Long> taskIdByClientKey = new LinkedHashMap<>();

        for (WorkflowTaskCreateRequest taskRequest : request.tasks()) {
            Long parentTaskId = resolveTaskId(taskRequest.parentTaskKey(), taskIdByClientKey);
            List<Long> blockedByTaskIds = taskRequest.blockedByTaskKeys() == null
                    ? List.of()
                    : taskRequest.blockedByTaskKeys().stream()
                    .map(taskKey -> resolveTaskId(taskKey, taskIdByClientKey))
                    .toList();

            TaskRecord task = taskService.create(userId, new TaskCreateRequest(
                    workflow.id(),
                    parentTaskId,
                    taskRequest.clientTaskKey(),
                    taskRequest.name(),
                    taskRequest.description(),
                    taskRequest.taskType(),
                    taskRequest.maxRetries(),
                    taskRequest.sourceType(),
                    taskRequest.sourceRef(),
                    taskRequest.input(),
                    taskRequest.metadata(),
                    blockedByTaskIds
            ));
            taskIdByClientKey.put(taskRequest.clientTaskKey(), task.id());
        }
        return get(userId, workflow.id());
    }

    @Override
    public WorkflowRecord get(Long userId, Long workflowId) {
        return workflowRepository.findById(userId, workflowId)
                .orElseThrow(() -> new ApplicationException("工作流不存在或无权访问"));
    }

    @Override
    public List<TaskRecord> listTasks(Long userId, Long workflowId) {
        return taskService.listByWorkflow(userId, workflowId);
    }

    private Long resolveTaskId(String clientTaskKey, Map<String, Long> taskIdByClientKey) {
        if (clientTaskKey == null || clientTaskKey.isBlank()) {
            return null;
        }
        Long taskId = taskIdByClientKey.get(clientTaskKey);
        if (taskId == null) {
            throw new ApplicationException("工作流任务依赖引用不存在: " + clientTaskKey);
        }
        return taskId;
    }
}
