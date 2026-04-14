package com.example.agentplatform.chat.service;

import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.service.ModelUsageLogService;
import org.springframework.stereotype.Service;

/**
 * 鑱婂ぉ usage 鍐欏叆鏈嶅姟銆? * 璐熻矗鎶婅亰澶┿€丷AG銆丄gent 绛夐摼璺殑妯″瀷璋冪敤鎴愭湰鏍囧噯鍖栬惤搴撱€? */
@Service
public class ChatUsageService {

    private final ModelUsageLogService modelUsageLogService;

    public ChatUsageService(ModelUsageLogService modelUsageLogService) {
        this.modelUsageLogService = modelUsageLogService;
    }

    /**
     * 鍏煎鏃ц皟鐢ㄦ柟寮忕殑 usage 璁板綍鍏ュ彛銆?     */
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
     * 璁板綍涓€鏉″甫宸ヤ綔娴佷笂涓嬫枃鐨?usage 鏁版嵁銆?     */
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
