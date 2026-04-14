package com.example.agentplatform.agent.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.agent.domain.TaskPlan;
import com.example.agentplatform.chat.service.DirectPromptService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AgentProperties;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.domain.MemoryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 任务规划服务测试。
 * 验证结构化输出能够被正确转换为 TaskPlan。
 */
class TaskPlanningServiceTest {

    private ChatModel chatModel;
    private TaskPlanningService taskPlanningService;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        DirectPromptService directPromptService = mock(DirectPromptService.class);
        when(directPromptService.buildSystemPrompt(any())).thenReturn("你是测试用的 Agent。");

        AgentProperties agentProperties = new AgentProperties(
                true,
                AgentReasoningMode.COT,
                new AgentProperties.Cot(0.0, 512),
                new AgentProperties.Planning(true, 0.0, 768),
                new AgentProperties.Loop(6, 0.0, 512),
                new AgentProperties.Subagent(true, 4, true, 2, 2, List.of("search_web", "fetch_webpage"))
        );
        AiModelProperties aiModelProperties = new AiModelProperties("qwen3.5-flash", "qwen3-vl-embedding", "qwen3-vl-rerank");
        AgentPromptService agentPromptService = new AgentPromptService(directPromptService);
        taskPlanningService = new TaskPlanningService(
                chatModel,
                aiModelProperties,
                agentProperties,
                agentPromptService,
                new SpringAiChatResponseMapper()
        );
    }

    @Test
    void shouldConvertStructuredOutputToTaskPlan() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0, Prompt.class);
            assertThat(prompt.getInstructions())
                    .extracting(message -> message.getMessageType())
                    .contains(MessageType.SYSTEM, MessageType.USER);
            assertThat(prompt.getInstructions().stream().filter(SystemMessage.class::isInstance).findFirst().orElseThrow().getText())
                    .contains("结构化任务计划");
            assertThat(prompt.getInstructions().stream().filter(UserMessage.class::isInstance).findFirst().orElseThrow().getText())
                    .contains("用户目标");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {
                      "goal": "生成检索增强方案",
                      "planSummary": "先分析需求，再检索资料，最后输出方案。",
                      "steps": [
                        {
                          "stepId": "step-1",
                          "title": "分析需求",
                          "description": "梳理用户目标和约束",
                          "dependsOnStepIds": [],
                          "suggestedTools": [],
                          "skippable": false,
                          "doneCondition": "明确目标和边界"
                        },
                        {
                          "stepId": "step-2",
                          "title": "检索资料",
                          "description": "通过搜索工具获取资料",
                          "dependsOnStepIds": ["step-1"],
                          "suggestedTools": ["search_web"],
                          "skippable": false,
                          "doneCondition": "获得足够参考资料"
                        }
                      ]
                    }
                    """))));
        });

        TaskPlanningService.StructuredResult<TaskPlan> result = taskPlanningService.plan(
                AgentReasoningMode.REACT,
                "请帮我生成检索增强方案",
                new MemoryContext(List.of(), List.of(), List.of(), ""),
                List.of()
        );

        assertThat(result.body()).isNotNull();
        assertThat(result.body().goal()).isEqualTo("生成检索增强方案");
        assertThat(result.body().planSummary()).isEqualTo("先分析需求，再检索资料，最后输出方案。");
        assertThat(result.body().steps()).hasSize(2);
        assertThat(result.body().steps().get(0).stepId()).isEqualTo("step-1");
        assertThat(result.body().steps().get(1).suggestedTools()).containsExactly("search_web");
    }
}