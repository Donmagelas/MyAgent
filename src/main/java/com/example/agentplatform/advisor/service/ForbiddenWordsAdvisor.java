package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.AdvisorOperation;
import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AdvisorProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 简单内容安全 advisor。
 * 在模型调用前拦截包含配置违禁词的请求。
 */
@Service
public class ForbiddenWordsAdvisor implements ChatAdvisor {

    private final AdvisorProperties advisorProperties;

    public ForbiddenWordsAdvisor(AdvisorProperties advisorProperties) {
        this.advisorProperties = advisorProperties;
    }

    @Override
    public String getName() {
        return "ForbiddenWordsAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public void before(ChatAdvisorContext context) {
        if (context.operation() == AdvisorOperation.KNOWLEDGE_RETRIEVE) {
            return;
        }
        if (context.message() == null || context.message().isBlank()) {
            return;
        }
        String normalized = context.message().toLowerCase(Locale.ROOT);
        List<String> forbiddenWords = advisorProperties.forbiddenWords();
        if (forbiddenWords == null || forbiddenWords.isEmpty()) {
            return;
        }
        forbiddenWords.stream()
                .filter(word -> word != null && !word.isBlank())
                .filter(word -> normalized.contains(word.toLowerCase(Locale.ROOT)))
                .findFirst()
                .ifPresent(word -> {
                    throw new ApplicationException("Input contains forbidden content: " + word);
                });
    }
}
