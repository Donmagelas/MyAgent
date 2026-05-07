package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.AdvisorOperation;
import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.advisor.domain.RequestSafetyDecision;
import com.example.agentplatform.config.AdvisorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 请求安全 advisor。
 * 在进入统一 Agent Loop 前判断用户请求是否有害、违法、越权或涉及敏感数据索取。
 */
@Service
public class RequestSafetyAdvisor implements ChatAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RequestSafetyAdvisor.class);

    private final AdvisorProperties advisorProperties;
    private final RequestSafetyClassifier requestSafetyClassifier;

    public RequestSafetyAdvisor(
            AdvisorProperties advisorProperties,
            RequestSafetyClassifier requestSafetyClassifier
    ) {
        this.advisorProperties = advisorProperties;
        this.requestSafetyClassifier = requestSafetyClassifier;
    }

    @Override
    public String getName() {
        return "RequestSafetyAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public void before(ChatAdvisorContext context) {
        Evaluation evaluation = evaluate(context);
        if (!evaluation.shouldBlock(advisorProperties.requestSafety().blockConfidenceThreshold())) {
            return;
        }

        log.warn(
                "请求安全 advisor 拦截请求: category={}, confidence={}, reason={}",
                evaluation.decision().category(),
                evaluation.decision().confidence(),
                evaluation.decision().reason()
        );
        throw new AccessDeniedException(evaluation.refusalMessage());
    }

    /**
     * 返回请求安全评估结果，供主链路在持久化消息后复用并记录 usage。
     */
    public Evaluation evaluate(ChatAdvisorContext context) {
        AdvisorProperties.RequestSafety requestSafety = advisorProperties.requestSafety();
        if (!requestSafety.enabled()) {
            return Evaluation.allow(requestSafety.refusalMessage());
        }
        if (context.operation() != AdvisorOperation.AGENT_STREAM) {
            return Evaluation.allow(requestSafety.refusalMessage());
        }
        if (context.message() == null || context.message().isBlank()) {
            return Evaluation.allow(requestSafety.refusalMessage());
        }

        RequestSafetyClassifier.StructuredResult result = requestSafetyClassifier.classifyStructured(
                context.message(),
                context.authentication()
        );
        return new Evaluation(
                result.decision(),
                result.response(),
                requestSafety.refusalMessage()
        );
    }

    /**
     * 返回当前生效的拦截置信度阈值，供主链复用同一份配置。
     */
    public double blockConfidenceThreshold() {
        return advisorProperties.requestSafety().blockConfidenceThreshold();
    }

    /**
     * 请求安全评估结果。
     * 同时保留模型原始响应，便于在不展示过程的前提下记录 token usage。
     */
    public record Evaluation(
            RequestSafetyDecision decision,
            ChatClientResponse response,
            String refusalMessage
    ) {

        public static Evaluation allow(String refusalMessage) {
            return new Evaluation(
                    new RequestSafetyDecision(
                            com.example.agentplatform.advisor.domain.RequestSafetyAction.ALLOW,
                            "SAFE",
                            0.0d,
                            ""
                    ),
                    null,
                    refusalMessage
            );
        }

        public boolean shouldBlock(double threshold) {
            return decision != null && decision.shouldBlock(threshold);
        }
    }
}
