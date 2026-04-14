package com.example.agentplatform.agent.domain;

/**
 * subagent 完成类型。
 * 用于区分正常完成、工具直接返回以及各种保护性提前结束场景。
 */
public enum SubagentCompletionType {

    /** 正常得到最终答案。 */
    FINAL,

    /** 工具通过 returnDirect 直接返回结果。 */
    RETURN_DIRECT,

    /** 连续重复相同动作，触发保护性提前结束。 */
    REPEATED_ACTION_GUARD,

    /** 连续没有有效进展，触发保护性提前结束。 */
    NO_PROGRESS_GUARD,

    /** 达到最大轮数后回退结束。 */
    MAX_TURNS_FALLBACK
}