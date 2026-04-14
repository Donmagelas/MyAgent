package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import com.example.agentplatform.memory.dto.LongTermMemoryWriteRequest;

import java.util.List;

/**
 * 长期记忆服务。
 * 提供稳定事实的写入与按条件查询能力。
 */
public interface LongTermMemoryService {

    /** 写入一条长期记忆。 */
    LongTermMemory save(LongTermMemoryWriteRequest request);

    /** 按条件查询长期记忆。 */
    List<LongTermMemory> query(LongTermMemoryQueryRequest request);
}
