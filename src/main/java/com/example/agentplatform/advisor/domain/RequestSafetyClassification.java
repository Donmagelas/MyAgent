package com.example.agentplatform.advisor.domain;

/**
 * 模型返回的原始请求安全分类结果。
 * 保留字符串字段，降低结构化输出因枚举漂移导致的解析失败风险。
 */
public record RequestSafetyClassification(
        String action,
        String category,
        Double confidence,
        String reason
) {
}
