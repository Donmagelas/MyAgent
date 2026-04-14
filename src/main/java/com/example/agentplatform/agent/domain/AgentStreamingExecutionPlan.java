package com.example.agentplatform.agent.domain;

import com.example.agentplatform.agent.service.AgentExecutionWorkflowService;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RetrievedChunk;

import java.util.List;

/**
 * Agent 流式最终回答执行计划。
 * 用于在统一 Agent Loop 完成步骤决策后，把“最终回答生成”交给真正的模型流式调用处理。
 */
public record AgentStreamingExecutionPlan(
        Long userId,
        AgentExecutionWorkflowService.ExecutionWorkflow executionWorkflow,
        Conversation conversation,
        MemoryContext memoryContext,
        AgentReasoningMode mode,
        String originalMessage,
        String answerDraft,
        String reasoningSummary,
        int stepCount,
        List<String> toolNames,
        List<ChatAskResponse.SourceItem> sources,
        List<RetrievedChunk> retrievedChunks,
        boolean directReturn,
        String usageStepName
) {
}
