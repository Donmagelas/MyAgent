package com.example.agentplatform.tools.dto;

import java.util.List;

/**
 * 聚会地点推荐项。
 */
public record MeetupPlaceRecommendation(
        int rank,
        String name,
        String address,
        String location,
        String poiType,
        double score,
        int totalDurationMinutes,
        int maxDurationMinutes,
        double varianceMinutes,
        Integer centerDistanceMeters,
        List<MeetupRouteCost> routeCosts,
        String reason
) {
}
