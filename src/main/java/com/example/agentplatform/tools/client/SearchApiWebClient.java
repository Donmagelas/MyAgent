package com.example.agentplatform.tools.client;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tools.dto.SearchResultItem;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.dto.SearchToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 WebClient 的搜索 API 客户端。
 * 当前默认按 SearchApi.io 的参数格式发送请求，相关地址和密钥均通过配置外置。
 */
@Component
public class SearchApiWebClient implements SearchApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ToolProperties toolProperties;

    public SearchApiWebClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            ToolProperties toolProperties
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.toolProperties = toolProperties;
    }

    @Override
    public SearchToolResult search(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            throw new ApplicationException("Search query must not be blank");
        }
        String apiKey = toolProperties.searchApi().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApplicationException("Search API key is missing");
        }

        int resolvedLimit = limit == null || limit <= 0
                ? toolProperties.searchApi().defaultResultLimit()
                : limit;
        String response = executeSearchRequest(query, resolvedLimit, apiKey);

        if (response == null || response.isBlank()) {
            throw new ApplicationException("Search API returned an empty response");
        }
        return parseResponse(query, response, resolvedLimit);
    }

    /**
     * 执行搜索请求，并对网络抖动做有限次重试。
     */
    private String executeSearchRequest(String query, int resolvedLimit, String apiKey) {
        Duration timeout = toolProperties.searchApi().timeout();
        Duration retryBackoff = toolProperties.searchApi().retryBackoff();
        int maxAttempts = Math.max(toolProperties.searchApi().maxRetries(), 0) + 1;
        URI requestUri = buildRequestUri(query, resolvedLimit, apiKey);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return webClient.get()
                        .uri(requestUri)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(timeout);
            }
            catch (Exception exception) {
                if (!shouldRetry(exception) || attempt >= maxAttempts) {
                    throw new ApplicationException("Search API request failed", exception);
                }
                sleepBeforeRetry(retryBackoff);
            }
        }

        throw new ApplicationException("Search API request failed");
    }

    /**
     * 构建搜索请求地址，并确保查询参数按 URL 规则编码。
     */
    private URI buildRequestUri(String query, int resolvedLimit, String apiKey) {
        return UriComponentsBuilder.fromUriString(toolProperties.searchApi().baseUrl())
                .queryParam("engine", toolProperties.searchApi().engine())
                .queryParam("q", query)
                .queryParam("num", resolvedLimit)
                .queryParam("api_key", apiKey)
                .build()
                .encode()
                .toUri();
    }

    /**
     * 仅对临时性网络错误和 5xx 响应做重试，避免对参数错误反复请求。
     */
    private boolean shouldRetry(Exception exception) {
        if (exception instanceof ApplicationException) {
            return false;
        }
        if (exception instanceof WebClientRequestException) {
            return true;
        }
        if (exception instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    /**
     * 两次重试之间短暂等待，降低瞬时网络抖动带来的失败概率。
     */
    private void sleepBeforeRetry(Duration retryBackoff) {
        if (retryBackoff == null || retryBackoff.isZero() || retryBackoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff.toMillis());
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ApplicationException("Search API retry interrupted", interruptedException);
        }
    }

    /**
     * 解析搜索 API 返回的 JSON 响应，并统一抽取通用搜索结果。
     */
    private SearchToolResult parseResponse(String query, String response, int resolvedLimit) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.path("organic_results");
            List<SearchResultItem> items = new ArrayList<>();
            if (resultsNode.isArray()) {
                for (JsonNode itemNode : resultsNode) {
                    if (items.size() >= resolvedLimit) {
                        break;
                    }
                    items.add(new SearchResultItem(
                            itemNode.path("position").asInt(items.size() + 1),
                            itemNode.path("title").asText(""),
                            itemNode.path("link").asText(""),
                            itemNode.path("snippet").asText("")
                    ));
                }
            }
            return new SearchToolResult(query, items.size(), items);
        }
        catch (Exception exception) {
            throw new ApplicationException("Failed to parse search API response", exception);
        }
    }
}
