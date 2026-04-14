package com.example.agentplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 记忆系统配置。
 * 集中维护短期记忆窗口、长期记忆返回数量和摘要召回参数。
 */
@ConfigurationProperties(prefix = "app.memory")
public record MemoryProperties(
        Integer shortTermWindowSize,
        Integer stableFactLimit,
        Integer summaryTopK,
        Integer summarySimilarityTopK,
        List<String> enabledMemoryTypes,
        Extraction extraction
) {

    public MemoryProperties {
        shortTermWindowSize = shortTermWindowSize == null ? 8 : shortTermWindowSize;
        stableFactLimit = stableFactLimit == null ? 10 : stableFactLimit;
        summaryTopK = summaryTopK == null ? 5 : summaryTopK;
        summarySimilarityTopK = summarySimilarityTopK == null ? 8 : summarySimilarityTopK;
        enabledMemoryTypes = enabledMemoryTypes == null ? List.of(
                "USER_PREFERENCE",
                "PROJECT_STATUS",
                "DESIGN_DECISION",
                "TASK_CONCLUSION",
                "STABLE_FACT"
        ) : List.copyOf(enabledMemoryTypes);
        extraction = extraction == null ? new Extraction(
                true,
                5,
                8,
                true,
                0.0,
                768,
                4,
                List.of(
                        "喜欢",
                        "偏好",
                        "决定",
                        "结论",
                        "完成",
                        "进度",
                        "上线",
                        "要求",
                        "preference",
                        "prefer",
                        "decision",
                        "result",
                        "completed",
                        "milestone"
                )
        ) : extraction;
    }

    /**
     * 自动长期记忆提炼配置。
     * 用于控制周期触发、重要内容触发以及结构化提炼模型参数。
     */
    public record Extraction(
            boolean enabled,
            Integer periodicTurnInterval,
            Integer recentMessageWindow,
            boolean summaryEnabled,
            Double temperature,
            Integer maxTokens,
            Integer maxMemoriesPerRun,
            List<String> importantKeywords
    ) {

        public Extraction {
            periodicTurnInterval = periodicTurnInterval == null ? 3 : periodicTurnInterval;
            recentMessageWindow = recentMessageWindow == null ? 8 : recentMessageWindow;
            temperature = temperature == null ? 0.0 : temperature;
            maxTokens = maxTokens == null ? 768 : maxTokens;
            maxMemoriesPerRun = maxMemoriesPerRun == null ? 4 : maxMemoriesPerRun;
            importantKeywords = importantKeywords == null ? List.of() : List.copyOf(importantKeywords);
        }
    }
}
