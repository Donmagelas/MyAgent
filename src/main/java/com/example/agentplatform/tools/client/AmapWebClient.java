package com.example.agentplatform.tools.client;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AmapProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 高德地图 Web API 客户端。
 * 第一版只封装地址解析、周边 POI 搜索和路线耗时计算，供聚会地点推荐工具使用。
 */
@Component
public class AmapWebClient implements AmapClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AmapProperties amapProperties;
    private final Object rateLimitMonitor = new Object();
    private long lastRequestAtMillis = 0L;

    public AmapWebClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            AmapProperties amapProperties
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.amapProperties = amapProperties;
    }

    @Override
    public Optional<GeoPoint> geocode(String address, String city) {
        ensureEnabled();
        if (!StringUtils.hasText(address)) {
            return Optional.empty();
        }

        URI requestUri = baseBuilder("/v3/geocode/geo")
                .queryParam("address", address.trim())
                .queryParamIfPresent("city", text(city))
                .build()
                .encode()
                .toUri();
        JsonNode root = executeJsonRequest(requestUri, "地址解析");
        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            return Optional.empty();
        }

        JsonNode first = geocodes.get(0);
        return parsePoint(first.path("location").asText(""))
                .map(point -> new GeoPoint(
                        point.longitude(),
                        point.latitude(),
                        first.path("formatted_address").asText(address)
                ));
    }

    @Override
    public List<PoiCandidate> searchAround(GeoPoint center, String keyword, int radiusMeters, int limit) {
        ensureEnabled();
        if (center == null) {
            return List.of();
        }
        String resolvedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "餐厅";
        int resolvedLimit = Math.max(1, limit);
        int resolvedRadius = Math.max(1, radiusMeters);

        URI requestUri = baseBuilder("/v3/place/around")
                .queryParam("location", center.toLocation())
                .queryParam("keywords", resolvedKeyword)
                .queryParam("radius", resolvedRadius)
                .queryParam("offset", resolvedLimit)
                .queryParam("page", 1)
                .queryParam("extensions", "all")
                .build()
                .encode()
                .toUri();
        JsonNode root = executeJsonRequest(requestUri, "周边搜索");
        JsonNode pois = root.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }

        List<PoiCandidate> candidates = new ArrayList<>();
        for (JsonNode poi : pois) {
            if (candidates.size() >= resolvedLimit) {
                break;
            }
            String location = poi.path("location").asText("");
            if (!StringUtils.hasText(location)) {
                continue;
            }
            candidates.add(new PoiCandidate(
                    poi.path("id").asText(""),
                    poi.path("name").asText(""),
                    readText(poi.path("address")),
                    location,
                    poi.path("type").asText(""),
                    parseInteger(poi.path("distance").asText(""))
            ));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.distanceMeters() == null
                        ? Integer.MAX_VALUE
                        : candidate.distanceMeters()))
                .toList();
    }

    @Override
    public Optional<RouteMetrics> route(GeoPoint origin, GeoPoint destination, String city, String mode) {
        ensureEnabled();
        if (origin == null || destination == null) {
            return Optional.empty();
        }
        String normalizedMode = StringUtils.hasText(mode) ? mode.trim().toLowerCase() : amapProperties.routeMode();
        URI requestUri = switch (normalizedMode) {
            case "driving" -> drivingRouteUri(origin, destination);
            case "walking" -> walkingRouteUri(origin, destination);
            default -> transitRouteUri(origin, destination, city);
        };
        JsonNode root = executeJsonRequest(requestUri, "路线规划");
        return parseRouteMetrics(root, normalizedMode);
    }

    private UriComponentsBuilder baseBuilder(String path) {
        String apiKey = amapProperties.apiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new ApplicationException("高德 API key 未配置");
        }
        return UriComponentsBuilder.fromUriString(amapProperties.baseUrl() + path)
                .queryParam("key", apiKey);
    }

    private URI transitRouteUri(GeoPoint origin, GeoPoint destination, String city) {
        return baseBuilder("/v3/direction/transit/integrated")
                .queryParam("origin", origin.toLocation())
                .queryParam("destination", destination.toLocation())
                .queryParamIfPresent("city", text(city))
                .queryParamIfPresent("cityd", text(city))
                .build()
                .encode()
                .toUri();
    }

    private URI drivingRouteUri(GeoPoint origin, GeoPoint destination) {
        return baseBuilder("/v3/direction/driving")
                .queryParam("origin", origin.toLocation())
                .queryParam("destination", destination.toLocation())
                .queryParam("extensions", "base")
                .build()
                .encode()
                .toUri();
    }

    private URI walkingRouteUri(GeoPoint origin, GeoPoint destination) {
        return baseBuilder("/v3/direction/walking")
                .queryParam("origin", origin.toLocation())
                .queryParam("destination", destination.toLocation())
                .build()
                .encode()
                .toUri();
    }

    private JsonNode executeJsonRequest(URI requestUri, String operation) {
        String response = executeRequest(requestUri, operation);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText(""))) {
                String info = root.path("info").asText("unknown error");
                throw new ApplicationException("高德 API " + operation + "失败: " + info);
            }
            return root;
        }
        catch (ApplicationException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new ApplicationException("高德 API " + operation + "响应解析失败", exception);
        }
    }

    /**
     * 执行 HTTP 请求，并对临时网络错误做有限重试。
     */
    private String executeRequest(URI requestUri, String operation) {
        Duration timeout = amapProperties.timeout();
        Duration retryBackoff = amapProperties.retryBackoff();
        int maxAttempts = Math.max(amapProperties.maxRetries(), 0) + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                waitForRateLimit();
                String response = webClient.get()
                        .uri(requestUri)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(timeout);
                if (!StringUtils.hasText(response)) {
                    throw new ApplicationException("高德 API " + operation + "返回为空");
                }
                return response;
            }
            catch (Exception exception) {
                if (!shouldRetry(exception) || attempt >= maxAttempts) {
                    throw new ApplicationException("高德 API " + operation + "请求失败", exception);
                }
                sleepBeforeRetry(retryBackoff);
            }
        }
        throw new ApplicationException("高德 API " + operation + "请求失败");
    }

    /**
     * 高德免费额度通常有 3 次/秒的并发上限。
     * 这里用客户端内的串行限速避免一次聚会推荐连续路线请求触发 CUQPS 限制。
     */
    private void waitForRateLimit() {
        Duration minRequestInterval = amapProperties.minRequestInterval();
        if (minRequestInterval == null || minRequestInterval.isZero() || minRequestInterval.isNegative()) {
            return;
        }
        synchronized (rateLimitMonitor) {
            long now = System.currentTimeMillis();
            long waitMillis = minRequestInterval.toMillis() - (now - lastRequestAtMillis);
            if (waitMillis > 0) {
                sleepBeforeRetry(Duration.ofMillis(waitMillis));
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }

    private Optional<RouteMetrics> parseRouteMetrics(JsonNode root, String mode) {
        JsonNode route = root.path("route");
        if ("transit".equals(mode) || !("driving".equals(mode) || "walking".equals(mode))) {
            JsonNode transits = route.path("transits");
            if (!transits.isArray() || transits.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = transits.get(0);
            Integer duration = parseInteger(first.path("duration").asText(""));
            Integer distance = parseInteger(first.path("distance").asText(route.path("distance").asText("")));
            return duration == null ? Optional.empty() : Optional.of(new RouteMetrics(duration, distance));
        }

        JsonNode paths = route.path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = paths.get(0);
        Integer duration = parseInteger(first.path("duration").asText(""));
        Integer distance = parseInteger(first.path("distance").asText(""));
        return duration == null ? Optional.empty() : Optional.of(new RouteMetrics(duration, distance));
    }

    private Optional<GeoPoint> parsePoint(String location) {
        if (!StringUtils.hasText(location)) {
            return Optional.empty();
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new GeoPoint(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    ""
            ));
        }
        catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (!node.isArray()) {
            return node.asText("");
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        return String.join(",", parts);
    }

    private Integer parseInteger(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private Optional<String> text(String value) {
        return StringUtils.hasText(value) ? Optional.of(value.trim()) : Optional.empty();
    }

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

    private void sleepBeforeRetry(Duration retryBackoff) {
        if (retryBackoff == null || retryBackoff.isZero() || retryBackoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff.toMillis());
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ApplicationException("高德 API 请求重试被中断", interruptedException);
        }
    }

    private void ensureEnabled() {
        if (!amapProperties.enabled()) {
            throw new ApplicationException("高德地图工具未启用");
        }
    }
}
