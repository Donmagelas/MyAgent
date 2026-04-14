package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatAskResponse;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.memory.advisor.MemoryContextAdvisor;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.rag.domain.RagEvidenceAssessment;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.rag.service.RagAnswerJudgeService;
import com.example.agentplatform.rag.service.RagEvidenceGuardService;
import com.example.agentplatform.rag.service.SpringAiRagAdvisorFactory;
import com.example.agentplatform.rag.service.SpringAiRetrievedDocumentMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 检索增强聊天服务。
 * 在调用聊天模型前使用 Spring AI RetrievalAugmentationAdvisor 完成查询增强、检索和上下文拼装。
 */
@Service
public class RetrievalAugmentedChatService {

    private final ChatPersistenceService chatPersistenceService;
    private final SpringAiQuestionAnswerPromptService springAiQuestionAnswerPromptService;
    private final ChatUsageService chatUsageService;
    private final MemoryContextAdvisor memoryContextAdvisor;
    private final SpringAiRagAdvisorFactory springAiRagAdvisorFactory;
    private final SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final RagEvidenceGuardService ragEvidenceGuardService;
    private final RagAnswerJudgeService ragAnswerJudgeService;
    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;

    public RetrievalAugmentedChatService(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            ChatPersistenceService chatPersistenceService,
            SpringAiQuestionAnswerPromptService springAiQuestionAnswerPromptService,
            ChatUsageService chatUsageService,
            MemoryContextAdvisor memoryContextAdvisor,
            SpringAiRagAdvisorFactory springAiRagAdvisorFactory,
            SpringAiRetrievedDocumentMapper springAiRetrievedDocumentMapper,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            RagEvidenceGuardService ragEvidenceGuardService,
            RagAnswerJudgeService ragAnswerJudgeService
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.chatPersistenceService = chatPersistenceService;
        this.springAiQuestionAnswerPromptService = springAiQuestionAnswerPromptService;
        this.chatUsageService = chatUsageService;
        this.memoryContextAdvisor = memoryContextAdvisor;
        this.springAiRagAdvisorFactory = springAiRagAdvisorFactory;
        this.springAiRetrievedDocumentMapper = springAiRetrievedDocumentMapper;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.ragEvidenceGuardService = ragEvidenceGuardService;
        this.ragAnswerJudgeService = ragAnswerJudgeService;
    }

    /**
     * 使用传入的检索结果执行一次 grounded chat 请求。
     * 实际问答阶段仍通过 RetrievalAugmentationAdvisor 再执行一次完整的增强检索，以确保查询转换和扩展生效。
     */
    @Transactional
    public ChatAskResponse ask(ChatAskRequest request, List<RetrievedChunk> chunks, Long userId) {
        Conversation conversation = chatPersistenceService.getOrCreateConversation(userId, request);
        ChatMessage userMessage = chatPersistenceService.saveUserMessage(userId, conversation.id(), request.message());
        MemoryContext memoryContext = memoryContextAdvisor.buildContext(userId, conversation.id(), request.message());
        RagEvidenceAssessment evidenceAssessment = ragEvidenceGuardService.assess(request.message(), chunks);

        Instant start = Instant.now();
        try {
            if (!evidenceAssessment.sufficient()) {
                String fallbackAnswer = ragEvidenceGuardService.buildInsufficientAnswer();
                ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                        userId,
                        conversation.id(),
                        fallbackAnswer,
                        aiModelProperties.chatModel()
                );
                chatUsageService.save(
                        conversation.id(),
                        assistantMessage.id(),
                        "chat-grounded-evidence-gate",
                        null,
                        Duration.between(start, Instant.now()).toMillis(),
                        true,
                        evidenceAssessment.reason()
                );
                return new ChatAskResponse(
                        conversation.id(),
                        conversation.sessionId(),
                        fallbackAnswer,
                        springAiRetrievedDocumentMapper.toSourceItems(chunks)
                );
            }

            ChatClientResponse clientResponse = chatClient.prompt()
                    .options(new DefaultChatOptionsBuilder()
                            .model(aiModelProperties.chatModel())
                            .temperature(aiModelProperties.chatTemperature())
                            .build())
                    .advisors(springAiRagAdvisorFactory.createRetrievalAugmentationAdvisor())
                    .system(springAiQuestionAnswerPromptService.buildSystemPrompt(memoryContext))
                    .user(request.message())
                    .call()
                    .chatClientResponse();
            ChatCompletionClient.ChatCompletionResult response =
                    springAiChatResponseMapper.toResult(clientResponse.chatResponse());
            List<RetrievedChunk> actualChunks = springAiRetrievedDocumentMapper.fromAdvisorContext(clientResponse.context());
            if (actualChunks.isEmpty()) {
                actualChunks = springAiRetrievedDocumentMapper.fromResponseMetadata(clientResponse.chatResponse().getMetadata());
            }
            List<RetrievedChunk> sourceChunks = springAiRetrievedDocumentMapper.mergeWithFallback(actualChunks, chunks);
            RagAnswerJudgeService.StructuredJudgeResult judgeResult =
                    ragAnswerJudgeService.judge(request.message(), response.answer(), sourceChunks);
            if (judgeResult.response() != null) {
                chatUsageService.save(
                        conversation.id(),
                        userMessage.id(),
                        "chat-grounded-judge",
                        springAiChatResponseMapper.toResult(judgeResult.response().chatResponse()),
                        Duration.between(start, Instant.now()).toMillis(),
                        true,
                        null
                );
            }
            String finalAnswer = resolveFinalAnswer(response.answer(), judgeResult);
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            ChatMessage assistantMessage = chatPersistenceService.saveAssistantMessage(
                    userId,
                    conversation.id(),
                    finalAnswer,
                    response.modelName()
            );
            chatUsageService.save(
                    conversation.id(),
                    assistantMessage.id(),
                    "chat-grounded-answer",
                    response,
                    latencyMs,
                    true,
                    null
            );
            return new ChatAskResponse(
                    conversation.id(),
                    conversation.sessionId(),
                    finalAnswer,
                    springAiRetrievedDocumentMapper.toSourceItems(sourceChunks)
            );
        }
        catch (Exception exception) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            chatUsageService.save(
                    conversation.id(),
                    userMessage.id(),
                    "chat-grounded-answer",
                    null,
                    latencyMs,
                    false,
                    exception.getMessage()
            );
            throw new ApplicationException("Grounded chat generation failed", exception);
        }
    }

    /**
     * 根据回答后 judge 结果决定最终返回的安全答案。
     */
    private String resolveFinalAnswer(
            String answer,
            RagAnswerJudgeService.StructuredJudgeResult judgeResult
    ) {
        if (judgeResult == null || judgeResult.body() == null) {
            return answer;
        }
        if (!judgeResult.body().downgradeToInsufficient()) {
            return answer;
        }
        String safeAnswer = judgeResult.body().safeAnswer();
        return safeAnswer == null || safeAnswer.isBlank() ? answer : safeAnswer;
    }
}
