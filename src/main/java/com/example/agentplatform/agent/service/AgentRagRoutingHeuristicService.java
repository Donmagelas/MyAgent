package com.example.agentplatform.agent.service;

import com.example.agentplatform.config.AgentRagRoutingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Agent 内部的轻量级 RAG 路由启发式服务。
 * 在不增加额外模型调用的前提下，用于识别更像知识型问题的请求，
 * 并在首步规划过于保守时，将 FINAL 或搜索工具动作改写为优先检索知识库。
 */
@Service
public class AgentRagRoutingHeuristicService {

    /**
     * 检测结构化标识。
     * 这类标识通常意味着用户在询问字段、配置项、路径、接口名或文档内实体。
     */
    private static final Pattern STRUCTURED_IDENTIFIER_PATTERN = Pattern.compile(
            "(?i)(`[^`]+`|\b[a-z0-9]+(?:[._/-][a-z0-9]+)+\b|\b[a-z]+[A-Z][A-Za-z0-9]+\b)"
    );

    private final AgentRagRoutingProperties properties;

    public AgentRagRoutingHeuristicService(AgentRagRoutingProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断当前问题是否应该更积极地进入一次知识库检索。
     */
    public RagRoutingDecision decide(String message) {
        if (!properties.enabled() || message == null || message.isBlank()) {
            return RagRoutingDecision.skip("RAG 路由启发式未启用或消息为空");
        }

        String normalized = normalize(message);
        if (normalized.length() < properties.minQueryLength()) {
            return RagRoutingDecision.skip("问题过短，不提升 RAG 倾向");
        }
        if (containsAny(normalized, properties.blockedKeywords())) {
            return RagRoutingDecision.skip("命中闲聊或创作类阻断词，不提升 RAG 倾向");
        }

        int positiveSignals = 0;
        List<String> reasonParts = new ArrayList<>();

        if (containsAny(normalized, properties.questionKeywords())) {
            positiveSignals++;
            reasonParts.add("命中知识型问题词");
        }
        if (containsAny(normalized, properties.domainKeywords())) {
            positiveSignals++;
            reasonParts.add("命中领域事实词");
        }
        if (containsStructuredIdentifier(message)) {
            positiveSignals++;
            reasonParts.add("命中结构化标识");
        }

        if (positiveSignals < properties.minPositiveSignals()) {
            return RagRoutingDecision.skip("知识型问题信号不足，保持模型原决策");
        }

        return RagRoutingDecision.force("知识型问题启发式命中：" + String.join(" + ", reasonParts));
    }

    /**
     * 统一归一化大小写和空白，降低简单包含判断的噪声。
     */
    private String normalize(String message) {
        return message.toLowerCase(Locale.ROOT)
                .replace('？', '?')
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('：', ' ')
                .replace('；', ' ')
                .replace('、', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    /**
     * 判断是否包含任意配置化关键词。
     */
    private boolean containsAny(String message, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(message::contains);
    }

    /**
     * 判断消息中是否带有类似字段路径、文件名、接口名、代码标识符等结构化信号。
     */
    private boolean containsStructuredIdentifier(String message) {
        return STRUCTURED_IDENTIFIER_PATTERN.matcher(message).find();
    }

    /**
     * RAG 路由启发式的判定结果。
     */
    public record RagRoutingDecision(
            boolean forceRag,
            String reason
    ) {

        public static RagRoutingDecision force(String reason) {
            return new RagRoutingDecision(true, reason);
        }

        public static RagRoutingDecision skip(String reason) {
            return new RagRoutingDecision(false, reason);
        }
    }
}
