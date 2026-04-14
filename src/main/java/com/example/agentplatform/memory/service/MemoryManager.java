package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.memory.domain.MemoryQuery;

/**
 * 统一记忆管理入口。
 * 负责在回答前按固定顺序组装可复用的记忆上下文。
 */
public interface MemoryManager {

    /** 组装当前问题所需的统一记忆上下文。 */
    MemoryContext buildContext(MemoryQuery query);
}
