package com.example.agentplatform.observability.service;

import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.dto.ModelUsageLogEntry;
import com.example.agentplatform.observability.repository.ModelUsageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面向应用层的 usage 日志服务。
 * 负责写入模型调用成本，并提供流程级查询能力。
 */
@Service
public class ModelUsageLogService {

    private final ModelUsageRepository modelUsageRepository;

    public ModelUsageLogService(ModelUsageRepository modelUsageRepository) {
        this.modelUsageRepository = modelUsageRepository;
    }

    /**
     * 持久化一条模型 usage 记录。
     */
    public void save(ModelUsageRecord record) {
        modelUsageRepository.save(record);
    }

    /**
     * 查询某个工作流下的 usage 明细。
     */
    public List<ModelUsageLogEntry> findByWorkflowId(Long workflowId) {
        return modelUsageRepository.findByWorkflowId(workflowId);
    }
}
