package com.example.agentplatform.tools.dto;

/**
 * 网页抓取结果。
 */
public record WebPageFetchResult(
        String url,
        String title,
        String content,
        int contentLength
) {
}
