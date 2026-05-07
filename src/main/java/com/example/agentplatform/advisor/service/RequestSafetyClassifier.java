package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.RequestSafetyDecision;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.security.core.Authentication;

/**
 * 请求安全分类器。
 * 负责识别用户请求是否存在有害、越权或敏感数据外流风险。
 */
public interface RequestSafetyClassifier {

    /**
     * 对当前用户请求做安全判别，并保留底层 ChatResponse 便于记录 usage。
     */
    StructuredResult classifyStructured(String message, Authentication authentication);

    /**
     * 供不关心 usage 的调用方使用的简化入口。
     */
    default RequestSafetyDecision classify(String message, Authentication authentication) {
        return classifyStructured(message, authentication).decision();
    }

    /**
     * 安全分类与原始模型响应的组合结果。
     */
    record StructuredResult(
            RequestSafetyDecision decision,
            ChatClientResponse response
    ) {
    }
}
