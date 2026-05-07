package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.RequestSafetyAction;
import com.example.agentplatform.advisor.domain.RequestSafetyClassification;
import com.example.agentplatform.advisor.domain.RequestSafetyDecision;
import com.example.agentplatform.chat.service.SpringAiChatResponseMapper;
import com.example.agentplatform.config.AdvisorProperties;
import com.example.agentplatform.config.AiModelProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 基于 Spring AI 的请求安全分类器。
 * 在进入主 Agent Loop 前识别有害请求、危险行为和越权意图。
 */
@Service
public class SpringAiRequestSafetyClassifier implements RequestSafetyClassifier {

    /**
     * 使用稳定英文 prompt，降低 Windows / PowerShell 编辑链路导致的 prompt 污染风险。
     * 该 prompt 故意覆盖更广的安全风险，而不是只聚焦某一类越权或凭证索取场景。
     */
    static final String SYSTEM_PROMPT = """
            You are a general safety, abuse-prevention, and authorization classifier for an enterprise agent platform.
            Classify whether the user's request should be allowed to enter the main agent loop.

            Return BLOCK when the request asks for or strongly implies any of the following:
            - obtaining, revealing, exporting, guessing, cracking, or bypassing passwords, tokens, API keys, cookies, sessions, private keys, verification codes, or similar credentials
            - unauthorized access, privilege escalation, security bypass, disabling protections, malware delivery, phishing, ransomware, account takeover, or clearly harmful cyber abuse
            - accessing, exposing, or mass-exporting other people's private data, account data, player data, secrets, location data, surveillance data, or full database contents without legitimate need
            - violent wrongdoing, weapons, explosives, poisoning, arson, terrorism, targeted harassment, doxxing, stalking, kidnapping, or instructions that would materially help physical harm
            - fraud, extortion, evading law enforcement, forging identity, scam enablement, criminal planning, or other clearly illegal and socially harmful conduct
            - self-harm or suicide instructions that focus on means, optimization, concealment, or increasing lethality
            - instructions that materially help privacy invasion, covert surveillance, credential theft, or dangerous misuse even if phrased as an experiment

            Return ALLOW when the request is benign, defensive, analytical, or compliance-oriented, including:
            - how to protect passwords, rotate keys, secure systems, redact private data, moderate abuse, or audit for leaks
            - explaining why a request would be unsafe, illegal, or unauthorized
            - incident response, secure design, compliance review, abuse prevention, content moderation, and policy analysis
            - documentation analysis that mentions sensitive nouns but does not request harmful, illegal, or abusive access
            - high-level historical, journalistic, academic, or policy discussion that does not request operationally useful harmful instructions

            Important rules:
            1. Do not block a request merely because it mentions words like password, token, key, database, weapon, or malware.
            2. Focus on the user's intent, requested outcome, and operational utility, not isolated keywords.
            3. If the request materially helps harmful, illegal, violent, invasive, deceptive, or unauthorized behavior, prefer BLOCK.
            4. Authenticated roles do not automatically justify disclosing secrets, bulk private data, or unsafe instructions.
            5. If the request is clearly about defense, prevention, auditing, or refusal explanation, prefer ALLOW.
            6. Return structured output only.

            Use one category from:
            SAFE
            CREDENTIAL_THEFT
            SENSITIVE_DATA_EXFILTRATION
            PRIVILEGE_ESCALATION
            SECURITY_BYPASS
            MALWARE_OR_CYBER_ABUSE
            PRIVACY_INTRUSION
            VIOLENCE_OR_WEAPONIZATION
            FRAUD_OR_DECEPTION
            SELF_HARM
            ILLEGAL_OR_DANGEROUS_ACTIVITY
            OTHER
            """;

    private final ChatClient chatClient;
    private final AiModelProperties aiModelProperties;
    private final AdvisorProperties advisorProperties;
    private final SpringAiChatResponseMapper springAiChatResponseMapper;

    public SpringAiRequestSafetyClassifier(
            ChatModel chatModel,
            AiModelProperties aiModelProperties,
            AdvisorProperties advisorProperties,
            SpringAiChatResponseMapper springAiChatResponseMapper
    ) {
        this.chatClient = ChatClient.create(chatModel);
        this.aiModelProperties = aiModelProperties;
        this.advisorProperties = advisorProperties;
        this.springAiChatResponseMapper = springAiChatResponseMapper;
    }

    @Override
    public StructuredResult classifyStructured(String message, Authentication authentication) {
        BeanOutputConverter<RequestSafetyClassification> outputConverter =
                new BeanOutputConverter<>(RequestSafetyClassification.class);
        ChatClientResponse response = chatClient.prompt()
                .options(new DefaultChatOptionsBuilder()
                        .model(aiModelProperties.chatModel())
                        .temperature(advisorProperties.requestSafety().temperature())
                        .maxTokens(advisorProperties.requestSafety().maxTokens())
                        .build())
                .system(SYSTEM_PROMPT + "\n\n" + outputConverter.getFormat())
                .user(buildUserPrompt(message, authentication))
                .call()
                .chatClientResponse();
        String content = springAiChatResponseMapper.extractAnswer(response.chatResponse());
        RequestSafetyClassification classification = outputConverter.convert(content);
        return new StructuredResult(normalize(classification), response);
    }

    /**
     * 供测试锁定 prompt 关键约束，避免后续退化为只覆盖局部场景。
     */
    static String systemPromptTemplate() {
        return SYSTEM_PROMPT;
    }

    private String buildUserPrompt(String message, Authentication authentication) {
        String authorities = authentication == null || authentication.getAuthorities() == null
                ? "none"
                : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(", "));
        return """
                User request:
                %s

                Authenticated roles:
                %s

                Decide whether this request should be allowed to proceed.
                """.formatted(
                message == null ? "" : message,
                authorities.isBlank() ? "none" : authorities
        );
    }

    private RequestSafetyDecision normalize(RequestSafetyClassification classification) {
        RequestSafetyAction action = RequestSafetyAction.from(
                classification == null ? null : classification.action()
        );
        String category = classification == null || classification.category() == null || classification.category().isBlank()
                ? "OTHER"
                : classification.category().trim();
        double confidence = classification == null || classification.confidence() == null
                ? (action == RequestSafetyAction.BLOCK ? 1.0d : 0.0d)
                : Math.max(0.0d, Math.min(1.0d, classification.confidence()));
        String reason = classification == null || classification.reason() == null
                ? ""
                : classification.reason().trim();
        return new RequestSafetyDecision(action, category, confidence, reason);
    }
}
