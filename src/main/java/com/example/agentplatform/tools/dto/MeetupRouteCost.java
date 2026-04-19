package com.example.agentplatform.tools.dto;

/**
 * 单个参与人到候选地点的路线成本。
 */
public record MeetupRouteCost(
        String participantName,
        String originAddress,
        Integer durationMinutes,
        Integer distanceMeters,
        String status
) {
}
