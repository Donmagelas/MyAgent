package com.example.agentplatform.memory.service;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.repository.ChatMessageRepository;
import com.example.agentplatform.chat.service.ChatUsageService;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.domain.MemoryExtractionCandidate;
import com.example.agentplatform.memory.domain.MemoryExtractionResult;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.dto.LongTermMemoryWriteRequest;
import com.example.agentplatform.memory.dto.MemorySummaryWriteRequest;
import com.example.agentplatform.memory.repository.LongTermMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认自动记忆整理服务。
 * 按周期或重要内容触发，调用模型结构化提炼长期记忆，并同步写入记忆摘要。
 */
@Service
public class DefaultMemoryAutomationService implements MemoryAutomationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryAutomationService.class);

    private final MemoryProperties memoryProperties;
    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final ChatUsageService chatUsageService;
    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemorySummaryService memorySummaryService;
    private final MemoryExtractionPromptService memoryExtractionPromptService;
    private final MemoryExtractionTriggerPolicy memoryExtractionTriggerPolicy;
    private final MemoryExtractionCandidatePostProcessor memoryExtractionCandidatePostProcessor;
    private final ChatMessageRepository chatMessageRepository;
    private final TaskExecutor memoryExtractionTaskExecutor;

    public DefaultMemoryAutomationService(
            MemoryProperties memoryProperties,
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            ChatUsageService chatUsageService,
            ShortTermMemoryService shortTermMemoryService,
            LongTermMemoryService longTermMemoryService,
            LongTermMemoryRepository longTermMemoryRepository,
            MemorySummaryService memorySummaryService,
            MemoryExtractionPromptService memoryExtractionPromptService,
            MemoryExtractionTriggerPolicy memoryExtractionTriggerPolicy,
            MemoryExtractionCandidatePostProcessor memoryExtractionCandidatePostProcessor,
            ChatMessageRepository chatMessageRepository,
            @Qualifier("memoryExtractionTaskExecutor")
            TaskExecutor memoryExtractionTaskExecutor
    ) {
        this.memoryProperties = memoryProperties;
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.chatUsageService = chatUsageService;
        this.shortTermMemoryService = shortTermMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.memorySummaryService = memorySummaryService;
        this.memoryExtractionPromptService = memoryExtractionPromptService;
        this.memoryExtractionTriggerPolicy = memoryExtractionTriggerPolicy;
        this.memoryExtractionCandidatePostProcessor = memoryExtractionCandidatePostProcessor;
        this.chatMessageRepository = chatMessageRepository;
        this.memoryExtractionTaskExecutor = memoryExtractionTaskExecutor;
    }

    @Override
    public void triggerAfterAssistantMessage(Long userId, Long conversationId, ChatMessage assistantMessage) {
        if (!memoryProperties.extraction().enabled() || assistantMessage == null) {
            return;
        }
        List<RecentConversationMessage> recentMessages = shortTermMemoryService.loadRecentMessages(
                userId,
                conversationId,
                memoryProperties.extraction().recentMessageWindow()
        );
        int assistantTurnCount = chatMessageRepository.countByConversationIdAndRole(conversationId, "assistant");
        MemoryExtractionTriggerPolicy.Decision decision =
                memoryExtractionTriggerPolicy.decide(assistantTurnCount, recentMessages);
        if (!decision.triggered()) {
            return;
        }
        memoryExtractionTaskExecutor.execute(() ->
                extractAndPersist(userId, conversationId, assistantMessage, recentMessages, decision));
    }

    private void extractAndPersist(
            Long userId,
            Long conversationId,
            ChatMessage assistantMessage,
            List<RecentConversationMessage> recentMessages,
            MemoryExtractionTriggerPolicy.Decision decision
    ) {
        Instant startedAt = Instant.now();
        try {
            BeanOutputConverter<MemoryExtractionResult> outputConverter =
                    new BeanOutputConverter<>(MemoryExtractionResult.class);
            ChatClientResponse response = chatClient.prompt()
                    .options(new DefaultChatOptionsBuilder()
                            .model(aiModelProperties.chatModel())
                            .temperature(memoryProperties.extraction().temperature())
                            .maxTokens(memoryProperties.extraction().maxTokens())
                            .build())
                    .system(memoryExtractionPromptService.buildSystemPrompt(decision.triggerType())
                            + "\n\n"
                            + outputConverter.getFormat())
                    .user(memoryExtractionPromptService.buildUserPrompt(recentMessages))
                    .call()
                    .chatClientResponse();

            chatUsageService.save(
                    conversationId,
                    assistantMessage.id(),
                    buildUsageStepName(decision),
                    springAiChatResponseMapper.toResult(response.chatResponse()),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    true,
                    null
            );

            String content = springAiChatResponseMapper.extractAnswer(response.chatResponse());
            MemoryExtractionResult result = outputConverter.convert(content);
            if (result == null || !Boolean.TRUE.equals(result.shouldPersist())
                    || result.memories() == null || result.memories().isEmpty()) {
                return;
            }

            result.memories().stream()
                    .limit(memoryProperties.extraction().maxMemoriesPerRun())
                    .map(memoryExtractionCandidatePostProcessor::normalize)
                    .filter(this::isValidCandidate)
                    .forEach(candidate -> persistCandidate(userId, conversationId, assistantMessage, candidate, decision));
        }
        catch (Exception exception) {
            log.warn("自动长期记忆提炼失败: conversationId={}, assistantMessageId={}, reason={}",
                    conversationId, assistantMessage.id(), exception.getMessage(), exception);
        }
    }

    private void persistCandidate(
            Long userId,
            Long conversationId,
            ChatMessage assistantMessage,
            MemoryExtractionCandidate candidate,
            MemoryExtractionTriggerPolicy.Decision decision
    ) {
        if (longTermMemoryRepository.existsActiveDuplicate(
                userId,
                candidate.memoryType(),
                candidate.subject(),
                candidate.content()
        )) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("autoExtracted", true);
        metadata.put("triggerType", decision.triggerType().name());
        metadata.put("triggerReason", decision.reason());
        metadata.put("assistantMessageId", assistantMessage.id());

        LongTermMemory longTermMemory = longTermMemoryService.save(new LongTermMemoryWriteRequest(
                userId,
                conversationId,
                candidate.memoryType(),
                candidate.subject(),
                candidate.content(),
                candidate.importance(),
                true,
                "auto-extraction",
                "chat_message:" + assistantMessage.id(),
                metadata
        ));

        if (!memoryProperties.extraction().summaryEnabled()) {
            return;
        }
        memorySummaryService.save(new MemorySummaryWriteRequest(
                userId,
                longTermMemory.id(),
                conversationId,
                buildSummaryText(candidate),
                candidate.importance(),
                true,
                "auto-extraction",
                "chat_message:" + assistantMessage.id(),
                metadata
        ));
    }

    private boolean isValidCandidate(MemoryExtractionCandidate candidate) {
        return candidate != null
                && candidate.memoryType() != null
                && candidate.subject() != null
                && !candidate.subject().isBlank()
                && candidate.content() != null
                && !candidate.content().isBlank();
    }

    private String buildSummaryText(MemoryExtractionCandidate candidate) {
        if (candidate.summaryText() != null && !candidate.summaryText().isBlank()) {
            return candidate.summaryText();
        }
        return candidate.subject() + ": " + candidate.content();
    }

    private String buildUsageStepName(MemoryExtractionTriggerPolicy.Decision decision) {
        return "memory-auto-extraction-" + decision.triggerType().name().toLowerCase();
    }
}
