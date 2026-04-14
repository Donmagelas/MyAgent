package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.MemoryExtractionTriggerType;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 自动长期记忆提炼触发策略。
 * 先按会话轮次做周期触发，再按最近消息中的高价值关键词做即时触发。
 */
@Component
public class MemoryExtractionTriggerPolicy {

    private final MemoryProperties memoryProperties;

    public MemoryExtractionTriggerPolicy(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /**
     * 根据当前会话状态决定是否触发长期记忆自动提炼。
     */
    public Decision decide(int assistantTurnCount, List<RecentConversationMessage> recentMessages) {
        if (!memoryProperties.extraction().enabled()) {
            return Decision.noTrigger();
        }
        if (memoryProperties.extraction().periodicTurnInterval() > 0
                && assistantTurnCount > 0
                && assistantTurnCount % memoryProperties.extraction().periodicTurnInterval() == 0) {
            return new Decision(true, MemoryExtractionTriggerType.PERIODIC, "assistant-turn-interval");
        }
        if (containsImportantKeywords(recentMessages)) {
            return new Decision(true, MemoryExtractionTriggerType.IMPORTANT_CONTENT, "important-keyword");
        }
        return Decision.noTrigger();
    }

    private boolean containsImportantKeywords(List<RecentConversationMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return false;
        }
        List<String> keywords = memoryProperties.extraction().importantKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        int start = Math.max(recentMessages.size() - 2, 0);
        for (int index = start; index < recentMessages.size(); index++) {
            String content = recentMessages.get(index).content();
            if (content == null || content.isBlank()) {
                continue;
            }
            String normalized = content.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank()
                        && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 触发决策结果。
     */
    public record Decision(
            boolean triggered,
            MemoryExtractionTriggerType triggerType,
            String reason
    ) {

        public static Decision noTrigger() {
            return new Decision(false, null, null);
        }
    }
}
