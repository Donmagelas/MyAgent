package com.example.agentplatform.tools.dto;

import java.util.List;

/**
 * 聚会地点推荐工具结果。
 */
public record MeetupRecommendationResult(
        String city,
        String keyword,
        String transportMode,
        int participantCount,
        int candidateCount,
        List<MeetupPlaceRecommendation> recommendations,
        List<String> warnings
) {
}
