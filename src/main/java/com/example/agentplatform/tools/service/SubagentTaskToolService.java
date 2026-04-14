package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.tools.dto.SubagentTaskResult;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 子智能体任务工具。
 * 让父智能体可以把局部任务委托给独立上下文处理。
 */
@Component
public class SubagentTaskToolService {

    private static final String TOOL_NAME = "task";

    private final ToolPermissionGuard toolPermissionGuard;
    private final com.example.agentplatform.agent.service.SubagentService subagentService;

    public SubagentTaskToolService(
            ToolPermissionGuard toolPermissionGuard,
            com.example.agentplatform.agent.service.SubagentService subagentService
    ) {
        this.toolPermissionGuard = toolPermissionGuard;
        this.subagentService = subagentService;
    }

    /**
     * 在独立上下文里执行一个局部子任务，并返回摘要。
     */
    @Tool(
            name = TOOL_NAME,
            description = "在独立上下文中执行一个局部任务，并仅返回摘要结果，适合把高噪声子任务外包出去"
    )
    public SubagentTaskResult runTask(
            @ToolParam(description = "需要在独立上下文中执行的子任务 prompt") String prompt,
            ToolContext toolContext
    ) {
        toolPermissionGuard.assertAllowed(
                TOOL_NAME,
                Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                false,
                toolContext
        );
        return subagentService.run(prompt, toolContext);
    }
}
