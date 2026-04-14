package com.example.agentplatform.tools.dto;

import java.util.List;

/**
 * 联网搜索工具结果。
 */
public record SearchToolResult(
        String query,
        int resultCount,
        List<SearchResultItem> items
) {
}
