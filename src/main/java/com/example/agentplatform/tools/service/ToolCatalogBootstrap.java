package com.example.agentplatform.tools.service;

import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具目录引导器。
 * 应用启动后把本地已注册工具同步到数据库目录。
 */
@Component
public class ToolCatalogBootstrap implements ApplicationRunner {

    private final ToolCallbackRegistry toolCallbackRegistry;
    private final ToolCatalogService toolCatalogService;

    public ToolCatalogBootstrap(
            ToolCallbackRegistry toolCallbackRegistry,
            ToolCatalogService toolCatalogService
    ) {
        this.toolCallbackRegistry = toolCallbackRegistry;
        this.toolCatalogService = toolCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<PlatformToolDefinition> definitions = toolCallbackRegistry.getRegisteredTools().stream()
                .map(registeredTool -> registeredTool.definition())
                .toList();
        toolCatalogService.syncRegisteredTools(definitions, toolCallbackRegistry.getKnownToolNames());
    }
}
