package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AmapProperties;
import com.example.agentplatform.tools.client.AmapClient;
import com.example.agentplatform.tools.dto.MeetupParticipant;
import com.example.agentplatform.tools.dto.MeetupPlaceRecommendation;
import com.example.agentplatform.tools.dto.MeetupRecommendationResult;
import com.example.agentplatform.tools.dto.MeetupRouteCost;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 聚会地点推荐工具。
 * 基于高德地址解析、周边搜索和路线规划，给多人推荐相对公平的集合地点。
 */
@Component
public class MeetupRecommendationToolService {

    public static final String TOOL_NAME = "recommend_meetup_place";

    private static final int EARTH_RADIUS_METERS = 6_371_000;
    private static final int DEFAULT_RETURN_LIMIT = 3;

    private final AmapClient amapClient;
    private final AmapProperties amapProperties;
    private final ToolPermissionGuard toolPermissionGuard;

    public MeetupRecommendationToolService(
            AmapClient amapClient,
            AmapProperties amapProperties,
            ToolPermissionGuard toolPermissionGuard
    ) {
        this.amapClient = amapClient;
        this.amapProperties = amapProperties;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    /**
     * 推荐一个或多个适合多人见面的地点。
     */
    @Tool(
            name = TOOL_NAME,
            description = "根据多人所在位置、城市、地点关键词和交通方式，推荐相对公平的聚会或集合地点"
    )
    public MeetupRecommendationResult recommendMeetupPlace(
            @ToolParam(description = "城市名，例如北京、上海、苏州。公交路线规划建议必须提供城市") String city,
            @ToolParam(description = "参与人位置列表，至少包含 2 人。每项包含 name 和 address") List<MeetupParticipant> participants,
            @ToolParam(required = false, description = "候选地点关键词，例如餐厅、火锅、咖啡、商场，默认餐厅") String keyword,
            @ToolParam(required = false, description = "交通方式：transit 公共交通、driving 驾车、walking 步行，默认使用配置值") String transportMode,
            @ToolParam(required = false, description = "候选地点数量，建议 3 到 8") Integer candidateLimit,
            @ToolParam(required = false, description = "周边搜索半径，单位米，默认使用配置值") Integer radiusMeters,
            ToolContext toolContext
    ) {
        toolPermissionGuard.assertAllowed(
                TOOL_NAME,
                Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                false,
                toolContext
        );

        String resolvedCity = resolveCity(city);
        String resolvedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "餐厅";
        String resolvedMode = resolveTransportMode(transportMode);
        List<String> warnings = new ArrayList<>();
        List<ResolvedParticipant> resolvedParticipants = geocodeParticipants(participants, resolvedCity);
        int resolvedLimit = resolveCandidateLimit(candidateLimit, resolvedParticipants.size(), warnings);
        int resolvedRadius = resolveRadius(radiusMeters);

        AmapClient.GeoPoint center = calculateCenter(resolvedParticipants);
        List<AmapClient.PoiCandidate> candidates = amapClient.searchAround(
                center,
                resolvedKeyword,
                resolvedRadius,
                resolvedLimit
        );
        if (candidates.isEmpty()) {
            warnings.add("未从高德周边搜索获取到候选地点，请尝试扩大半径或更换关键词。");
            return new MeetupRecommendationResult(
                    resolvedCity,
                    resolvedKeyword,
                    resolvedMode,
                    resolvedParticipants.size(),
                    0,
                    List.of(),
                    warnings
            );
        }

        List<CandidateScore> scoredCandidates = scoreCandidates(
                resolvedParticipants,
                candidates,
                center,
                resolvedCity,
                resolvedMode,
                warnings
        );
        List<MeetupPlaceRecommendation> recommendations = toRecommendations(scoredCandidates);
        if (recommendations.isEmpty()) {
            warnings.add("候选地点均未获取到有效路线耗时，无法给出可靠排序。");
        }

        return new MeetupRecommendationResult(
                resolvedCity,
                resolvedKeyword,
                resolvedMode,
                resolvedParticipants.size(),
                candidates.size(),
                recommendations,
                warnings
        );
    }

    private String resolveCity(String city) {
        String resolvedCity = StringUtils.hasText(city) ? city.trim() : amapProperties.defaultCity();
        if (!StringUtils.hasText(resolvedCity)) {
            throw new ApplicationException("城市不能为空，请先确认聚会所在城市");
        }
        return resolvedCity;
    }

    private String resolveTransportMode(String transportMode) {
        String mode = StringUtils.hasText(transportMode) ? transportMode.trim().toLowerCase() : amapProperties.routeMode();
        if (!Set.of("transit", "driving", "walking").contains(mode)) {
            return amapProperties.routeMode();
        }
        return mode;
    }

    private int resolveCandidateLimit(Integer candidateLimit, int participantCount, List<String> warnings) {
        int limit = candidateLimit == null || candidateLimit <= 0
                ? amapProperties.defaultCandidateLimit()
                : candidateLimit;
        limit = Math.min(limit, amapProperties.maxCandidateLimit());
        int maxByRouteLimit = Math.max(1, amapProperties.maxRouteCalculations() / Math.max(participantCount, 1));
        if (limit > maxByRouteLimit) {
            warnings.add("候选地点数量已按路线计算上限缩减为 " + maxByRouteLimit + "。");
            limit = maxByRouteLimit;
        }
        return Math.max(1, limit);
    }

    private int resolveRadius(Integer radiusMeters) {
        int radius = radiusMeters == null || radiusMeters <= 0
                ? amapProperties.defaultRadiusMeters()
                : radiusMeters;
        return Math.min(radius, amapProperties.maxRadiusMeters());
    }

    private List<ResolvedParticipant> geocodeParticipants(List<MeetupParticipant> participants, String city) {
        if (participants == null || participants.size() < 2) {
            throw new ApplicationException("聚会地点推荐至少需要 2 个参与人位置");
        }
        if (participants.size() > amapProperties.maxParticipants()) {
            throw new ApplicationException("参与人数超过配置上限: " + amapProperties.maxParticipants());
        }

        List<ResolvedParticipant> resolvedParticipants = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            MeetupParticipant participant = participants.get(index);
            String name = normalizeName(participant, index);
            String address = participant == null ? "" : participant.address();
            if (!StringUtils.hasText(address)) {
                throw new ApplicationException("参与人缺少地址: " + name);
            }
            AmapClient.GeoPoint point = amapClient.geocode(address.trim(), city)
                    .orElseThrow(() -> new ApplicationException("无法解析参与人地址: " + name + "，" + address));
            resolvedParticipants.add(new ResolvedParticipant(name, address.trim(), point));
        }
        return resolvedParticipants;
    }

    private String normalizeName(MeetupParticipant participant, int index) {
        if (participant != null && StringUtils.hasText(participant.name())) {
            return participant.name().trim();
        }
        return "参与人" + (index + 1);
    }

    private AmapClient.GeoPoint calculateCenter(List<ResolvedParticipant> participants) {
        double longitude = participants.stream()
                .mapToDouble(participant -> participant.point().longitude())
                .average()
                .orElse(0d);
        double latitude = participants.stream()
                .mapToDouble(participant -> participant.point().latitude())
                .average()
                .orElse(0d);
        return new AmapClient.GeoPoint(longitude, latitude, "参与人位置中心点");
    }

    private List<CandidateScore> scoreCandidates(
            List<ResolvedParticipant> participants,
            List<AmapClient.PoiCandidate> candidates,
            AmapClient.GeoPoint center,
            String city,
            String transportMode,
            List<String> warnings
    ) {
        List<CandidateScore> scoredCandidates = new ArrayList<>();
        for (AmapClient.PoiCandidate candidate : candidates) {
            AmapClient.GeoPoint destination = parseCandidatePoint(candidate);
            List<MeetupRouteCost> routeCosts = new ArrayList<>();
            List<Integer> durations = new ArrayList<>();
            for (ResolvedParticipant participant : participants) {
                AmapClient.RouteMetrics metrics = amapClient.route(participant.point(), destination, city, transportMode)
                        .orElse(null);
                if (metrics == null) {
                    routeCosts.add(new MeetupRouteCost(
                            participant.name(),
                            participant.address(),
                            null,
                            null,
                            "路线不可用"
                    ));
                    continue;
                }
                int durationMinutes = Math.max(1, (int) Math.ceil(metrics.durationSeconds() / 60.0d));
                durations.add(durationMinutes);
                routeCosts.add(new MeetupRouteCost(
                        participant.name(),
                        participant.address(),
                        durationMinutes,
                        metrics.distanceMeters(),
                        "成功"
                ));
            }
            if (durations.isEmpty()) {
                warnings.add("候选地点缺少有效路线，已跳过: " + candidate.name());
                continue;
            }
            scoredCandidates.add(scoreCandidate(candidate, destination, center, routeCosts, durations));
        }
        return scoredCandidates.stream()
                .sorted(Comparator.comparingDouble(CandidateScore::score))
                .toList();
    }

    private AmapClient.GeoPoint parseCandidatePoint(AmapClient.PoiCandidate candidate) {
        String[] parts = candidate.location().split(",");
        if (parts.length != 2) {
            throw new ApplicationException("候选地点经纬度非法: " + candidate.name());
        }
        try {
            return new AmapClient.GeoPoint(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    candidate.name()
            );
        }
        catch (NumberFormatException exception) {
            throw new ApplicationException("候选地点经纬度非法: " + candidate.name(), exception);
        }
    }

    private CandidateScore scoreCandidate(
            AmapClient.PoiCandidate candidate,
            AmapClient.GeoPoint destination,
            AmapClient.GeoPoint center,
            List<MeetupRouteCost> routeCosts,
            List<Integer> durations
    ) {
        int totalDuration = durations.stream().mapToInt(Integer::intValue).sum();
        int maxDuration = durations.stream().mapToInt(Integer::intValue).max().orElse(totalDuration);
        double average = totalDuration / (double) durations.size();
        double variance = durations.stream()
                .mapToDouble(duration -> Math.pow(duration - average, 2))
                .average()
                .orElse(0d);
        int centerDistance = distanceMeters(center.latitude(), center.longitude(), destination.latitude(), destination.longitude());
        double score = totalDuration * amapProperties.score().totalDurationWeight()
                + maxDuration * amapProperties.score().maxDurationWeight()
                + Math.sqrt(variance) * amapProperties.score().varianceWeight()
                + (centerDistance / 1_000.0d) * amapProperties.score().centerDistanceWeight();
        return new CandidateScore(
                candidate,
                round(score),
                totalDuration,
                maxDuration,
                round(variance),
                centerDistance,
                routeCosts
        );
    }

    private List<MeetupPlaceRecommendation> toRecommendations(List<CandidateScore> scoredCandidates) {
        List<MeetupPlaceRecommendation> recommendations = new ArrayList<>();
        int limit = Math.min(DEFAULT_RETURN_LIMIT, scoredCandidates.size());
        for (int index = 0; index < limit; index++) {
            CandidateScore scored = scoredCandidates.get(index);
            AmapClient.PoiCandidate candidate = scored.candidate();
            recommendations.add(new MeetupPlaceRecommendation(
                    index + 1,
                    candidate.name(),
                    candidate.address(),
                    candidate.location(),
                    candidate.type(),
                    scored.score(),
                    scored.totalDurationMinutes(),
                    scored.maxDurationMinutes(),
                    scored.varianceMinutes(),
                    scored.centerDistanceMeters(),
                    scored.routeCosts(),
                    buildReason(scored)
            ));
        }
        return recommendations;
    }

    private String buildReason(CandidateScore scored) {
        return "总耗时约 " + scored.totalDurationMinutes()
                + " 分钟，单人最长约 " + scored.maxDurationMinutes()
                + " 分钟，通勤差异方差约 " + scored.varianceMinutes()
                + "，距离参与人中心点约 " + scored.centerDistanceMeters() + " 米。";
    }

    private int distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double latitudeDistance = Math.toRadians(lat2 - lat1);
        double longitudeDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ResolvedParticipant(
            String name,
            String address,
            AmapClient.GeoPoint point
    ) {
    }

    private record CandidateScore(
            AmapClient.PoiCandidate candidate,
            double score,
            int totalDurationMinutes,
            int maxDurationMinutes,
            double varianceMinutes,
            Integer centerDistanceMeters,
            List<MeetupRouteCost> routeCosts
    ) {
    }
}
