package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import com.example.agentplatform.memory.dto.LongTermMemoryWriteRequest;
import com.example.agentplatform.memory.repository.LongTermMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认长期记忆服务实现。
 * 当前只做显式写入和查询，不做自动抽取策略。
 */
@Service
public class DefaultLongTermMemoryService implements LongTermMemoryService {

    private final LongTermMemoryRepository longTermMemoryRepository;

    public DefaultLongTermMemoryService(LongTermMemoryRepository longTermMemoryRepository) {
        this.longTermMemoryRepository = longTermMemoryRepository;
    }

    @Override
    public LongTermMemory save(LongTermMemoryWriteRequest request) {
        return longTermMemoryRepository.save(request);
    }

    @Override
    public List<LongTermMemory> query(LongTermMemoryQueryRequest request) {
        return longTermMemoryRepository.query(request);
    }
}
