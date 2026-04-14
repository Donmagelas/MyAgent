package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.dto.WebPageFetchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * 网页抓取工具。
 */
@Component
public class WebPageFetchToolService {

    private static final String TOOL_NAME = "fetch_webpage";

    private final ToolProperties toolProperties;
    private final ToolPermissionGuard toolPermissionGuard;

    public WebPageFetchToolService(ToolProperties toolProperties, ToolPermissionGuard toolPermissionGuard) {
        this.toolProperties = toolProperties;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    /**
     * 抓取网页正文内容。
     */
    @Tool(
            name = TOOL_NAME,
            description = "抓取指定网页的标题和正文文本，适合进一步阅读单个网页内容"
    )
    public WebPageFetchResult fetchWebPage(
            @ToolParam(description = "需要抓取的网页地址") String url,
            ToolContext toolContext
    ) {
        toolPermissionGuard.assertAllowed(
                TOOL_NAME,
                Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                false,
                toolContext
        );
        validateUrl(url);

        try {
            Document document = Jsoup.connect(url)
                    .userAgent("agent-platform/1.0")
                    .timeout((int) toolProperties.webPage().timeout().toMillis())
                    .get();
            String text = document.body() == null ? "" : document.body().text();
            String normalized = normalizeContent(text);
            return new WebPageFetchResult(
                    url,
                    document.title(),
                    normalized,
                    normalized.length()
            );
        }
        catch (Exception exception) {
            throw new ApplicationException("Failed to fetch web page", exception);
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !toolProperties.webPage().allowedSchemes().contains(scheme.toLowerCase())) {
                throw new ApplicationException("Unsupported URL scheme: " + url);
            }
        }
        catch (IllegalArgumentException exception) {
            throw new ApplicationException("Invalid URL: " + url, exception);
        }
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int maxLength = toolProperties.webPage().maxContentLength();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
