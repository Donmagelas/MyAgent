package com.example.agentplatform.tools.service;

import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.ToolCatalogEntry;
import com.example.agentplatform.tools.repository.ToolCatalogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具目录服务。
 * 负责维护数据库中的工具定义目录，并提供基础查询能力。
 */
@Service
public class ToolCatalogService {

    private final ToolCatalogRepository toolCatalogRepository;

    public ToolCatalogService(ToolCatalogRepository toolCatalogRepository) {
        this.toolCatalogRepository = toolCatalogRepository;
    }

    /**
     * 同步本地已注册工具到数据库目录。
     */
    public void syncRegisteredTools(List<PlatformToolDefinition> definitions) {
        syncRegisteredTools(
                definitions,
                definitions.stream().map(PlatformToolDefinition::name).toList()
        );
    }

    /**
     * 同步当前可用工具，并禁用已知但当前不可用的本地工具。
     */
    public void syncRegisteredTools(List<PlatformToolDefinition> definitions, List<String> knownToolNames) {
        for (PlatformToolDefinition definition : definitions) {
            toolCatalogRepository.upsert(definition);
        }
        Set<String> activeToolNames = definitions.stream()
                .map(PlatformToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> knownNames = knownToolNames == null ? Set.of() : Set.copyOf(knownToolNames);
        toolCatalogRepository.disableKnownToolsNotIn(knownNames, activeToolNames);
    }

    /**
     * 查询启用中的工具目录。
     */
    public List<ToolCatalogEntry> listEnabledTools(int limit) {
        return toolCatalogRepository.findAllEnabled(limit);
    }

    /**
     * 按问题文本搜索工具目录。
     */
    public List<ToolCatalogEntry> searchEnabledTools(String query, int limit) {
        return toolCatalogRepository.searchEnabled(query, limit);
    }
}
