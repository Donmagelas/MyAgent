package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆上下文组装器。
 * 统一把三层记忆按“短期记忆 -> 长期记忆 -> 记忆摘要”的顺序渲染为 prompt 上下文。
 */
@Service
public class MemoryContextAssembler {

    /** 组装统一记忆上下文。 */
    public MemoryContext assemble(
            List<LongTermMemory> stableFacts,
            List<RetrievedMemorySummary> recalledSummaries,
            List<RecentConversationMessage> recentMessages
    ) {
        StringBuilder builder = new StringBuilder();

        if (!recentMessages.isEmpty()) {
            builder.append("短期记忆（最近会话消息）:\n");
            for (RecentConversationMessage recentMessage : recentMessages) {
                builder.append("- ")
                        .append(recentMessage.role())
                        .append(": ")
                        .append(recentMessage.content())
                        .append('\n');
            }
            builder.append('\n');
        }

        if (!stableFacts.isEmpty()) {
            builder.append("长期记忆:\n");
            for (LongTermMemory stableFact : stableFacts) {
                builder.append("- [")
                        .append(stableFact.memoryType().name())
                        .append("][重要度=").append(stableFact.importance())
                        .append("][主题=").append(stableFact.subject()).append("] ")
                        .append(stableFact.content())
                        .append('\n');
            }
            builder.append('\n');
        }

        if (!recalledSummaries.isEmpty()) {
            builder.append("记忆摘要:\n");
            for (RetrievedMemorySummary recalledSummary : recalledSummaries) {
                builder.append("- [分数=")
                        .append(String.format("%.4f", recalledSummary.score()))
                        .append("] ")
                        .append(recalledSummary.summary().summaryText())
                        .append('\n');
            }
            builder.append('\n');
        }

        return new MemoryContext(
                List.copyOf(recentMessages),
                List.copyOf(stableFacts),
                List.copyOf(recalledSummaries),
                builder.toString().trim()
        );
    }
}
