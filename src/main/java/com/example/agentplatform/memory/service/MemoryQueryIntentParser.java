package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryQueryIntent;

/**
 * 记忆查询意图解析器。
 * 负责把自然语言问题转换为长期记忆结构化查询条件。
 */
public interface MemoryQueryIntentParser {

    /** 解析当前问题对应的长期记忆查询意图。 */
    MemoryQueryIntent parse(String question);
}
