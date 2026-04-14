package com.example.agentplatform.memory.dto;

import com.example.agentplatform.memory.domain.MemoryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 长期记忆创建请求。
 * 由当前登录用户为自己写入一条提炼后的稳定事实。
 */
public record LongTermMemoryCreateRequest(
        Long conversationId,
        @NotNull(message = "记忆类型不能为空")
        MemoryType memoryType,
        @NotBlank(message = "主题不能为空")
        String subject,
        @NotBlank(message = "内容不能为空")
        String content,
        @Min(value = 1, message = "重要度最小为 1")
        @Max(value = 10, message = "重要度最大为 10")
        Integer importance,
        Boolean active,
        String sourceType,
        String sourceRef,
        Map<String, Object> metadata
) {
}
