package com.example.agentplatform.rag.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * RAG 回答后校验结果。
 * 由模型根据问题、回答和证据进行结构化判断。
 */
public record RagAnswerJudgeResult(
        @JsonPropertyDescription("当前回答是否被给定证据充分支持。")
        boolean grounded,
        @JsonPropertyDescription("当回答不被证据支持时，是否应直接降级为证据不足答复。")
        boolean downgradeToInsufficient,
        @JsonPropertyDescription("对当前判断的简要原因说明。")
        String reason,
        @JsonPropertyDescription("当需要降级时，给用户的安全答复；如果无需降级可为空。")
        String safeAnswer
) {
}
