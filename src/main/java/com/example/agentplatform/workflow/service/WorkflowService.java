package com.example.agentplatform.workflow.service;

import com.example.agentplatform.tasks.domain.TaskRecord;
import com.example.agentplatform.workflow.domain.WorkflowRecord;
import com.example.agentplatform.workflow.dto.WorkflowCreateRequest;

import java.util.List;

/**
 * 工作流服务。
 */
public interface WorkflowService {

    WorkflowRecord create(Long userId, WorkflowCreateRequest request);

    WorkflowRecord get(Long userId, Long workflowId);

    List<TaskRecord> listTasks(Long userId, Long workflowId);
}
