package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentActionType;
import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.agent.dto.AgentChatResponse;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.tools.domain.RegisteredTool;

import java.util.List;

/**
 * Agent 执行监听器。
 * 用于在同步执行链外部感知步骤推进，从而支持 SSE 等流式输出场景。
 */
public interface AgentExecutionListener {

    /**
     * 当会话和工作流已建立时触发。
     */
    default void onStart(
            AgentReasoningMode mode,
            Long conversationId,
            String sessionId,
            Long workflowId
    ) {
    }

    /**
     * 当某一步推理规划完成时触发。
     */
    default void onPlanning(
            int stepIndex,
            String thought,
            AgentActionType actionType,
            String toolName
    ) {
    }

    /**
     * 当任务计划生成完成时触发。
     */
    default void onTaskPlan(TaskPlan taskPlan) {
    }

    /**
     * 当主链路完成 skill 路由和工具收敛后触发。
     */
    default void onSkillSelected(
            ResolvedSkill resolvedSkill,
            List<RegisteredTool> availableTools
    ) {
    }

    /**
     * 当程序完成 RAG 路由判断后触发。
     */
    default void onRagRoutingDecision(AgentRagRoutingService.RagRoutingDecision decision) {
    }

    /**
     * 当某一步 observation 产生时触发。
     */
    default void onObservation(
            int stepIndex,
            String toolName,
            String observation
    ) {
    }

    /**
     * 当 RAG 证据充分性 gate 拦截时触发。
     */
    default void onEvidenceGate(
            int stepIndex,
            String reason,
            String stepName
    ) {
    }

    /**
     * 当回答后 judge 判定需要降级时触发。
     */
    default void onJudgeDowngrade(
            int stepIndex,
            String reason,
            String stepName
    ) {
    }

    /**
     * 当某一步触发 RAG 检索并拿到来源时触发。
     */
    default void onRetrieval(
            int stepIndex,
            String query,
            List<ChatAskResponse.SourceItem> sources
    ) {
    }

    /**
     * 当一次模型 usage 记录完成落库时触发。
     */
    default void onUsage(ModelUsageRecord record) {
    }

    /**
     * 当最终结果生成时触发。
     */
    default void onFinal(AgentChatResponse response) {
    }

    /**
     * 当执行失败时触发。
     */
    default void onError(Throwable throwable) {
    }

    /**
     * 空监听器。
     */
    static AgentExecutionListener noop() {
        return new AgentExecutionListener() {
        };
    }
}
