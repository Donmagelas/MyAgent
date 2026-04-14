package com.example.agentplatform.memory.domain;

/**
 * 长期记忆类型枚举。
 * 当前只允许五类稳定事实进入长期记忆层。
 */
public enum MemoryType {
    USER_PREFERENCE,
    PROJECT_STATUS,
    DESIGN_DECISION,
    TASK_CONCLUSION,
    STABLE_FACT
}
