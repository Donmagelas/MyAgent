package com.example.agentplatform.observability.service;

import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.dto.ModelUsageLogEntry;
import com.example.agentplatform.observability.repository.ModelUsageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 闈㈠悜搴旂敤灞傜殑 usage 鏃ュ織鏈嶅姟銆? * 璐熻矗鍐欏叆妯″瀷璋冪敤鎴愭湰锛屽苟鎻愪緵娴佺▼绾ф煡璇㈣兘鍔涖€? */
@Service
public class ModelUsageLogService {

    private final ModelUsageRepository modelUsageRepository;

    public ModelUsageLogService(ModelUsageRepository modelUsageRepository) {
        this.modelUsageRepository = modelUsageRepository;
    }

    /**
     * 鎸佷箙鍖栦竴鏉℃ā鍨?usage 璁板綍銆?     */
    public void save(ModelUsageRecord record) {
        modelUsageRepository.save(record);
    }

    /**
     * 鏌ヨ鏌愪釜宸ヤ綔娴佷笅鐨?usage 鏄庣粏銆?     */
    public List<ModelUsageLogEntry> findByWorkflowId(Long workflowId) {
        return modelUsageRepository.findByWorkflowId(workflowId);
    }
}
