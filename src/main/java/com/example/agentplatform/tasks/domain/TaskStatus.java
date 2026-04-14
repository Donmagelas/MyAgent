package com.example.agentplatform.tasks.domain;

/**
 * 任务状态枚举。
 * 区分可执行、执行中和终态，便于工作流汇总状态。
 */
public enum TaskStatus {
    BLOCKED,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED;

    /** 当前状态是否已经进入终态。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
