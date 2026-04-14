package com.example.agentplatform.tools.client;

import com.example.agentplatform.tools.dto.SearchToolResult;

/**
 * 搜索 API 客户端。
 * 负责与外部搜索服务通信，并返回标准化搜索结果。
 */
public interface SearchApiClient {

    /**
     * 执行一次联网搜索。
     */
    SearchToolResult search(String query, Integer limit);
}
