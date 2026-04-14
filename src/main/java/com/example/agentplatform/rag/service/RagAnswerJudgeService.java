package com.example.agentplatform.rag.service;

import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.RagHallucinationProperties;
import com.example.agentplatform.rag.domain.RagAnswerJudgeResult;
import com.example.agentplatform.rag.domain.RetrievedChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 回答后校验服务。
 * 使用结构化输出判断当前回答是否被证据支持，并在必要时给出安全降级答复。
 */
@Service
public class RagAnswerJudgeService {

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final RagHallucinationProperties ragHallucinationProperties;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;
    private final RagEvidenceGuardService ragEvidenceGuardService;

    public RagAnswerJudgeService(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            RagHallucinationProperties ragHallucinationProperties,
            SpringAiChatResponseMapper springAiChatResponseMapper,
            RagEvidenceGuardService ragEvidenceGuardService
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.ragHallucinationProperties = ragHallucinationProperties;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
        this.ragEvidenceGuardService = ragEvidenceGuardService;
    }

    /**
     * 对 grounded-answer 进行回答后校验。
     */
    public StructuredJudgeResult judge(String question, String answer, List<RetrievedChunk> chunks) {
        if (!ragHallucinationProperties.enabled()
                || ragHallucinationProperties.judge() == null
                || !ragHallucinationProperties.judge().enabled()
                || chunks == null
                || chunks.isEmpty()) {
            return new StructuredJudgeResult(
                    new RagAnswerJudgeResult(true, false, "回答后 judge 已关闭", null),
                    null
            );
        }

        BeanOutputConverter<RagAnswerJudgeResult> outputConverter = new BeanOutputConverter<>(RagAnswerJudgeResult.class);
        ChatClientResponse response = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(ragHallucinationProperties.judge().temperature())
                        .maxTokens(ragHallucinationProperties.judge().maxTokens())
                        .build())
                .system(buildSystemPrompt() + "\n\n" + outputConverter.getFormat())
                .user(buildUserPrompt(question, answer, chunks))
                .call()
                .chatClientResponse();
        String content = springAiChatResponseMapper.extractAnswer(response.chatResponse());
        RagAnswerJudgeResult rawResult = outputConverter.convert(content);
        return new StructuredJudgeResult(normalize(rawResult), response);
    }

    private String buildSystemPrompt() {
        return """
                你是 RAG grounded-answer 的事实校验器。
                你的职责是判断“当前回答”是否完全被“给定证据”支持。
                判定规则：
                1. 如果回答包含证据中没有的具体事实、数字、结论或实体关系，则 grounded=false。
                2. 如果回答只是对证据的压缩复述，且没有新增事实，则 grounded=true。
                3. 如果证据不足以支撑一个确定性回答，应该建议降级为“证据不足”。
                4. safeAnswer 必须是直接给用户看的简洁中文答复，不要解释你的判断过程。
                5. 如果 grounded=true，则 safeAnswer 可为空。
                """;
    }

    private String buildUserPrompt(String question, String answer, List<RetrievedChunk> chunks) {
        int maxEvidenceChunks = Math.max(ragHallucinationProperties.judge().maxEvidenceChunks(), 1);
        int maxEvidenceChars = Math.max(ragHallucinationProperties.judge().maxEvidenceChars(), 120);
        String evidenceText = chunks.stream()
                .limit(maxEvidenceChunks)
                .map(chunk -> """
                        [来源]
                        title=%s
                        section=%s
                        type=%s
                        score=%.4f
                        content=%s
                        """.formatted(
                        prefer(chunk.chunkTitle(), chunk.documentTitle(), "unknown"),
                        prefer(chunk.sectionPath(), chunk.jsonPath(), "unknown"),
                        chunk.retrievalType(),
                        chunk.score(),
                        truncate(chunk.content(), maxEvidenceChars)
                ))
                .reduce("", (left, right) -> left + "\n" + right);

        return """
                用户问题：
                %s

                当前回答：
                %s

                检索证据：
                %s
                """.formatted(question, answer, evidenceText);
    }

    private RagAnswerJudgeResult normalize(RagAnswerJudgeResult rawResult) {
        if (rawResult == null) {
            return new RagAnswerJudgeResult(
                    false,
                    true,
                    "judge 结果为空",
                    ragEvidenceGuardService.buildInsufficientAnswer()
            );
        }
        if (rawResult.grounded()) {
            return new RagAnswerJudgeResult(true, false, rawResult.reason(), null);
        }
        String safeAnswer = rawResult.safeAnswer();
        if (safeAnswer == null || safeAnswer.isBlank()) {
            safeAnswer = ragEvidenceGuardService.buildInsufficientAnswer();
        }
        return new RagAnswerJudgeResult(false, true, rawResult.reason(), safeAnswer);
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private String prefer(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    /**
     * 返回结构化 judge 结果及其底层响应，便于记录 usage。
     */
    public record StructuredJudgeResult(
            RagAnswerJudgeResult body,
            ChatClientResponse response
    ) {
    }
}
