package com.example.agentplatform.tools.dto;

/**
 * 联网搜索结果项。
 */
public record SearchResultItem(
        int position,
        String title,
        String link,
        String snippet
) {
}
