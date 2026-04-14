package com.example.agentplatform.tasks.service;

import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.tasks.dto.TaskCreateRequest;
import com.example.agentplatform.tasks.dto.TaskStatusUpdateRequest;

import java.util.List;

/**
 * 任务服务。
 */
public interface TaskService {

    TaskRecord create(Long userId, TaskCreateRequest request);

    TaskRecord get(Long userId, Long taskId);

    List<TaskRecord> listByWorkflow(Long userId, Long workflowId);

    TaskRecord updateStatus(Long userId, Long taskId, TaskStatusUpdateRequest request);

    TaskRecord requestCancel(Long userId, Long taskId);
}
