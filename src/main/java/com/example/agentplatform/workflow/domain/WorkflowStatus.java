package com.example.agentplatform.workflow.domain;

/**
 * 工作流状态枚举。
 * 工作流是任务集合的编排容器，其状态由任务整体推进情况汇总而来。
 */
public enum WorkflowStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED;

    /** 当前状态是否已经进入终态。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
