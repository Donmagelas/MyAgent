package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.agent.service.SubagentService;
import com.example.agentplatform.config.AmapProperties;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.client.AmapClient;
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
                new SubagentTaskToolService(mock(ToolPermissionGuard.class), mock(SubagentService.class)),
                new MeetupRecommendationToolService(mock(AmapClient.class), testAmapProperties(), mock(ToolPermissionGuard.class)),
                testAmapProperties()
        );

        assertThat(registry.requireRegisteredTool("search_web").definition().allowedRoles())
                .contains(SecurityRole.CHAT_USER);
        assertThat(registry.requireRegisteredTool("fetch_webpage").definition().allowedRoles())
                .contains(SecurityRole.CHAT_USER);
        assertThat(registry.requireRegisteredTool("recommend_meetup_place").definition().allowedRoles())
                .contains(SecurityRole.CHAT_USER);
    }

    @Test
    void shouldHideMeetupToolWhenAmapIsDisabled() {
        AmapProperties disabledAmapProperties = amapProperties(false, "test");
        ToolCallbackRegistry registry = new ToolCallbackRegistry(
                new SearchToolService(mock(SearchApiClient.class), mock(ToolPermissionGuard.class)),
                new WebPageFetchToolService(testToolProperties(), mock(ToolPermissionGuard.class)),
                new PdfGenerateToolService(testToolProperties(), mock(ToolPermissionGuard.class)),
                new SubagentTaskToolService(mock(ToolPermissionGuard.class), mock(SubagentService.class)),
                new MeetupRecommendationToolService(mock(AmapClient.class), disabledAmapProperties, mock(ToolPermissionGuard.class)),
                disabledAmapProperties
        );

        assertThat(registry.findRegisteredTool("recommend_meetup_place")).isEmpty();
        assertThat(registry.getKnownToolNames()).contains("recommend_meetup_place");
    }

    @Test
    void shouldHideMeetupToolWhenAmapApiKeyIsMissing() {
        AmapProperties missingKeyAmapProperties = amapProperties(true, "");
        ToolCallbackRegistry registry = new ToolCallbackRegistry(
                new SearchToolService(mock(SearchApiClient.class), mock(ToolPermissionGuard.class)),
                new WebPageFetchToolService(testToolProperties(), mock(ToolPermissionGuard.class)),
                new PdfGenerateToolService(testToolProperties(), mock(ToolPermissionGuard.class)),
                new SubagentTaskToolService(mock(ToolPermissionGuard.class), mock(SubagentService.class)),
                new MeetupRecommendationToolService(mock(AmapClient.class), missingKeyAmapProperties, mock(ToolPermissionGuard.class)),
                missingKeyAmapProperties
        );

        assertThat(registry.findRegisteredTool("recommend_meetup_place")).isEmpty();
        assertThat(registry.getRegisteredTools())
                .extracting(registeredTool -> registeredTool.definition().name())
                .doesNotContain("recommend_meetup_place");
    }

    private ToolProperties testToolProperties() {
        return new ToolProperties(
                true,
                6,
                new ToolProperties.Resolver(6),
                new ToolProperties.SearchApi("https://example.com", "test", "google", 5, Duration.ofSeconds(5), 1, Duration.ofSeconds(1)),
                new ToolProperties.WebPage(Duration.ofSeconds(5), 1000, Set.of("https", "http")),
                new ToolProperties.Pdf(Path.of("generated-pdf"), "agent-platform")
        );
    }

    private AmapProperties testAmapProperties() {
        return amapProperties(true, "test");
    }

    private AmapProperties amapProperties(boolean enabled, String apiKey) {
        return new AmapProperties(
                enabled,
                "https://restapi.amap.com",
                apiKey,
                Duration.ofSeconds(5),
                1,
                Duration.ofMillis(100),
                Duration.ofMillis(400),
                "苏州",
                5_000,
                15_000,
                3,
                8,
                "transit",
                8,
                80,
                new AmapProperties.Score(0.45d, 0.30d, 0.20d, 0.05d)
        );
    }
}
