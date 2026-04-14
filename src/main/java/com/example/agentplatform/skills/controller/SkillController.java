package com.example.agentplatform.skills.controller;

import com.example.agentplatform.skills.domain.ResolvedSkill;
import com.example.agentplatform.skills.dto.SkillDefinitionResponse;
import com.example.agentplatform.skills.dto.SkillRoutePreviewRequest;
import com.example.agentplatform.skills.dto.SkillRoutePreviewResponse;
import com.example.agentplatform.skills.router.SkillRouterService;
import com.example.agentplatform.skills.service.SkillCatalogService;
import com.example.agentplatform.skills.service.SkillPromptBuilder;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Skill 模块接口。
 */
@Validated
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final String BASE_PROMPT = "你是一个支持技能路由的工具代理。";

    private final SkillCatalogService skillCatalogService;
    private final SkillRouterService skillRouterService;
    private final SkillPromptBuilder skillPromptBuilder;

    public SkillController(
            SkillCatalogService skillCatalogService,
            SkillRouterService skillRouterService,
            SkillPromptBuilder skillPromptBuilder
    ) {
        this.skillCatalogService = skillCatalogService;
        this.skillRouterService = skillRouterService;
        this.skillPromptBuilder = skillPromptBuilder;
    }

    /**
     * 返回当前启用中的 skill 列表。
     */
    @GetMapping
    public Mono<List<SkillDefinitionResponse>> list() {
        return Mono.fromCallable(() -> skillCatalogService.listEnabledSkills().stream()
                        .map(skill -> new SkillDefinitionResponse(
                                skill.id(),
                                skill.name(),
                                skill.description(),
                                skill.enabled(),
                                skill.tags(),
                                skill.routeKeywords(),
                                skill.allowedTools(),
                                skill.toolChoiceMode().name(),
                                skill.examples()
                        ))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 预览当前问题会路由到哪个 skill。
     */
    @PostMapping("/route-preview")
    public Mono<SkillRoutePreviewResponse> routePreview(@Valid @RequestBody SkillRoutePreviewRequest request) {
        return Mono.fromCallable(() -> skillRouterService.route(request.message())
                        .map(this::toPreviewResponse)
                        .orElseGet(() -> new SkillRoutePreviewResponse(
                                null,
                                null,
                                "none",
                                "未匹配到可用 skill",
                                List.of(),
                                BASE_PROMPT
                        )))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private SkillRoutePreviewResponse toPreviewResponse(ResolvedSkill resolvedSkill) {
        return new SkillRoutePreviewResponse(
                resolvedSkill.skillDefinition().id(),
                resolvedSkill.skillDefinition().name(),
                resolvedSkill.routeStrategy(),
                resolvedSkill.reason(),
                resolvedSkill.skillDefinition().allowedTools(),
                skillPromptBuilder.build(BASE_PROMPT, resolvedSkill)
        );
    }
}
