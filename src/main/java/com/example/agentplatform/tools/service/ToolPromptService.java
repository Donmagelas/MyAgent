package com.example.agentplatform.tools.service;

import org.springframework.stereotype.Component;

/**
 * 工具对话提示词服务。
 * 负责约束模型在可用工具和普通回答之间做出合理选择。
 */
@Component
public class ToolPromptService {

    /**
     * 构建工具对话系统提示词。
     */
    public String buildSystemPrompt() {
        return """
                你是一个可调用工具的智能助手。
                当问题需要联网搜索、抓取网页或生成 PDF 时，优先使用提供给你的工具。
                工具调用必须基于真实需要，不要为了调用而调用。
                如果工具结果已经足够回答，就基于工具结果直接回答。
                如果工具生成结果被标记为需要直接返回，则不要再加工，直接返回工具结果。
                如果问题不需要工具，直接正常回答。
                """;
    }
}
