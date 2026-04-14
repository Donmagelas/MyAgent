package com.example.agentplatform.memory.service;

import com.example.agentplatform.memory.domain.MemoryExtractionTriggerType;
import com.example.agentplatform.memory.domain.MemoryType;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自动长期记忆提炼提示词服务。
 * 统一构造结构化提炼阶段的 system / user prompt，避免提示词散落在业务逻辑中。
 */
@Service
public class MemoryExtractionPromptService {

    /**
     * 构造自动长期记忆提炼的系统提示词。
     */
    public String buildSystemPrompt(MemoryExtractionTriggerType triggerType) {
        return """
                You are the long-term memory extraction module for an enterprise Agent system.
                Your task is to decide whether the latest conversation segment contains stable, high-value information
                worth promoting into long-term memory.

                Only extract information that belongs to one of these memory types:
                %s

                Extraction rules:
                1. Prefer stable facts, user preferences, project progress, design decisions, and task conclusions.
                2. Do not store generic chit-chat, greetings, repeated filler, or transient wording.
                3. Do not copy the whole conversation. Extract only concise, reusable facts.
                4. subject must be short, stable, and searchable.
                5. content must be factual and directly reusable.
                6. summaryText must be a compact retrieval-friendly summary of the memory.
                7. importance must be between 1 and 10.
                8. If there is nothing worth storing, set shouldPersist=false and return an empty memories list.

                Type mapping rules:
                - STABLE_FACT: identity, name, nickname, location, role, organization, fixed environment facts.
                - USER_PREFERENCE: likes, dislikes, response style, tone, format, recurring personal preferences.
                - PROJECT_STATUS: current progress, milestones, release state, verification stage, rollout state.
                - DESIGN_DECISION: chosen technical方案, architectural decisions, accepted/rejected approaches.
                - TASK_CONCLUSION: final result, completed outcome, explicit conclusion of a specific task.

                Important constraints:
                - A person's name, identity, nickname, role, or location is NOT a USER_PREFERENCE. It must be STABLE_FACT.
                - "The user prefers concise answers" is USER_PREFERENCE.
                - "The user's name is Alice" is STABLE_FACT.
                - "The user lives in Hong Kong" is STABLE_FACT.
                - "Project codename is Nebula" is STABLE_FACT unless the conversation explicitly frames it as progress/status.
                - When unsure between USER_PREFERENCE and STABLE_FACT, prefer STABLE_FACT for identity/location/name facts.

                Current trigger type: %s
                """.formatted(renderMemoryTypes(), triggerType.name());
    }

    /**
     * 构造自动长期记忆提炼的用户提示词。
     */
    public String buildUserPrompt(List<RecentConversationMessage> recentMessages) {
        String transcript = recentMessages == null || recentMessages.isEmpty()
                ? "No recent conversation messages."
                : recentMessages.stream()
                .map(message -> "[%s] %s".formatted(message.role(), message.content()))
                .collect(Collectors.joining("\n"));
        return """
                Recent conversation window:
                %s

                Analyze this conversation window and return a structured MemoryExtractionResult.
                """.formatted(transcript);
    }

    private String renderMemoryTypes() {
        return Arrays.stream(MemoryType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
