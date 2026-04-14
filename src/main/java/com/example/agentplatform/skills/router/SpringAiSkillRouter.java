package com.example.agentplatform.skills.router;

import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.SkillRouteProperties;
import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.domain.SkillRouteDecision;
import com.example.agentplatform.skills.service.SkillCatalogService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI 结构化输出的 skill 路由器。
 * 让模型在给定 skill 元数据范围内返回结构化 skill 选择结果。
 */
@Component
public class SpringAiSkillRouter {

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final SkillRouteProperties skillRouteProperties;
    private final SkillCatalogService skillCatalogService;

    public SpringAiSkillRouter(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            SkillRouteProperties skillRouteProperties,
            SkillCatalogService skillCatalogService
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.skillRouteProperties = skillRouteProperties;
        this.skillCatalogService = skillCatalogService;
    }

    /**
     * 使用结构化输出选择 skill。
     */
    public Optional<ResolvedSkill> route(String message) {
        List<SkillDefinition> enabledSkills = skillCatalogService.listEnabledSkills();
        if (enabledSkills.isEmpty()) {
            return Optional.empty();
        }

        BeanOutputConverter<SkillRouteDecision> outputConverter = new BeanOutputConverter<>(SkillRouteDecision.class);
        SkillRouteDecision decision = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(skillRouteProperties.temperature())
                        .maxTokens(skillRouteProperties.maxTokens())
                        .build())
                .system(buildSystemPrompt(enabledSkills))
                .user(message == null || message.isBlank() ? "请为一个空问题选择最合适的 skill。" : message)
                .call()
                .entity(outputConverter);

        if (decision == null || !StringUtils.hasText(decision.skillId())) {
            return Optional.empty();
        }
        return skillCatalogService.findEnabledSkill(decision.skillId().trim())
                .map(skill -> new ResolvedSkill(skill, normalizeReason(decision.reason()), "structured"));
    }

    private String buildSystemPrompt(List<SkillDefinition> skills) {
        String skillDescription = skills.stream()
                .map(this::describeSkill)
                .collect(Collectors.joining("\n"));
        return """
                你是 skill 路由器。
                你的任务是根据用户问题，在给定的 skill 列表中选择最合适的一个。
                你只能返回列表中存在的 skillId。
                如果问题不明显属于某个专门技能，优先选择 general-tool-agent 作为兜底技能。
                只输出结构化结果，不要输出额外解释。

                可选 skill 列表：
                %s
                """.formatted(skillDescription);
    }

    private String describeSkill(SkillDefinition skill) {
        return """
                - skillId: %s
                  name: %s
                  description: %s
                  routeKeywords: %s
                  allowedTools: %s
                  examples: %s
                """.formatted(
                skill.id(),
                skill.name(),
                skill.description(),
                skill.routeKeywords(),
                skill.allowedTools(),
                skill.examples()
        );
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "模型返回了 skill 路由结果";
    }
}
