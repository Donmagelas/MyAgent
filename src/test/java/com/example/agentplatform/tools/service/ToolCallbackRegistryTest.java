package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.agent.service.SubagentService;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.client.SearchApiClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * ToolCallbackRegistry 测试。
 * 用于锁定只读联网工具对 CHAT_USER 的可用性，避免 subagent 对默认用户失效。
 */
class ToolCallbackRegistryTest {

    @Test
    void shouldExposeReadOnlyWebToolsToChatUser() {
        ToolProperties toolProperties = new ToolProperties(
                true,
                6,
                new ToolProperties.Resolver(6),
                new ToolProperties.SearchApi("https://example.com", "test", "google", 5, Duration.ofSeconds(5), 1, Duration.ofSeconds(1)),
                new ToolProperties.WebPage(Duration.ofSeconds(5), 1000, Set.of("https", "http")),
                new ToolProperties.Pdf(Path.of("generated-pdf"), "agent-platform")
        );
        ToolCallbackRegistry registry = new ToolCallbackRegistry(
                new SearchToolService(mock(SearchApiClient.class), mock(ToolPermissionGuard.class)),
                new WebPageFetchToolService(toolProperties, mock(ToolPermissionGuard.class)),
                new PdfGenerateToolService(toolProperties, mock(ToolPermissionGuard.class)),
                new SubagentTaskToolService(mock(ToolPermissionGuard.class), mock(SubagentService.class))
        );

        assertThat(registry.requireRegisteredTool("search_web").definition().allowedRoles())
                .contains(SecurityRole.CHAT_USER);
        assertThat(registry.requireRegisteredTool("fetch_webpage").definition().allowedRoles())
                .contains(SecurityRole.CHAT_USER);
    }
}
