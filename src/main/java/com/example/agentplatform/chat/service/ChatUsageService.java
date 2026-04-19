package com.example.agentplatform.chat.service;

import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.service.ModelUsageLogService;
import org.springframework.stereotype.Service;

/**
 * 聊天 usage 写入服务。
 * 负责把聊天、RAG、Agent 等链路的模型调用成本标准化落库。
 */
@Service
public class ChatUsageService {

    private final ModelUsageLogService modelUsageLogService;

    public ChatUsageService(ModelUsageLogService modelUsageLogService) {
        this.modelUsageLogService = modelUsageLogService;
    }

    /**
     * 兼容旧调用方式的 usage 记录入口。
     */
    public ModelUsageRecord save(
            Long conversationId,
            Long messageId,
            String stepName,
            ChatCompletionClient.ChatCompletionResult response,
            long latencyMs,
            boolean success,
            String errorMessage
    ) {
        return save(
                null,
                null,
                conversationId,
                messageId,
                stepName,
                response,
                latencyMs,
                success,
                errorMessage
        );
    }

    /**
     * 记录一条带工作流上下文的 usage 数据。
     */
    public ModelUsageRecord save(
            Long workflowId,
            Long taskId,
            Long conversationId,
            Long messageId,
            String stepName,
            ChatCompletionClient.ChatCompletionResult response,
            long latencyMs,
            boolean success,
            String errorMessage
    ) {
        ModelUsageRecord record = new ModelUsageRecord(
                workflowId,
                taskId,
                conversationId,
                messageId,
                response == null ? null : response.requestId(),
                stepName,
                response == null ? "unknown" : response.modelName(),
                "dashscope",
                response == null ? null : response.promptTokens(),
                response == null ? null : response.completionTokens(),
                response == null ? null : response.totalTokens(),
                latencyMs,
                success,
                errorMessage
        );
        modelUsageLogService.save(record);
        return record;
    }
}
