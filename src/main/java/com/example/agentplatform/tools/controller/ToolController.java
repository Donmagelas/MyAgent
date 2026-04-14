package com.example.agentplatform.tools.controller;

import com.example.agentplatform.tools.dto.ToolChatRequest;
import com.example.agentplatform.tools.dto.ToolChatResponse;
import com.example.agentplatform.tools.dto.ToolDefinitionResponse;
import com.example.agentplatform.tools.service.ToolCatalogService;
import com.example.agentplatform.tools.service.ToolChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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
 * 工具模块接口。
 */
@Validated
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolCatalogService toolCatalogService;
    private final ToolChatService toolChatService;

    public ToolController(ToolCatalogService toolCatalogService, ToolChatService toolChatService) {
        this.toolCatalogService = toolCatalogService;
        this.toolChatService = toolChatService;
    }

    /**
     * 返回当前已注册的工具定义。
     */
    @GetMapping("/definitions")
    public Mono<List<ToolDefinitionResponse>> definitions() {
        return Mono.fromCallable(() -> toolCatalogService.listEnabledTools(100).stream()
                        .map(definition -> new ToolDefinitionResponse(
                                definition.toolName(),
                                definition.description(),
                                definition.inputSchemaJson(),
                                definition.readOnly(),
                                definition.mutatesState(),
                                definition.dangerous(),
                                definition.returnDirect(),
                                definition.requiresApproval(),
                                definition.timeoutMillis(),
                                definition.riskLevel(),
                                definition.allowedRoles()
                        ))
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 发起一次工具对话。
     */
    @PostMapping("/chat")
    public Mono<ToolChatResponse> chat(@Valid @RequestBody ToolChatRequest request, Authentication authentication) {
        return Mono.fromCallable(() -> toolChatService.chat(request, authentication))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
