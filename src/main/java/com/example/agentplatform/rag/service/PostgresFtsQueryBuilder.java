package com.example.agentplatform.rag.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL FTS 查询构造器。
 * 把自然语言问题收敛成更适合 FTS 的关键词 OR 查询，避免长句被 plainto_tsquery 解析成过严的 AND 条件。
 */
@Component
public class PostgresFtsQueryBuilder {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}]+");

    /**
     * 构造 PostgreSQL to_tsquery 所需的查询表达式。
     * 例如把 "How does hybrid retrieval work with PostgreSQL FTS" 转成 "hybrid:* | retrieval:* | postgresql:* | fts:*"。
     */
    public String build(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }

        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (shouldSkip(token)) {
                continue;
            }
            tokens.add(token + ":*");
        }

        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" | ", tokens);
    }

    private boolean shouldSkip(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        if (token.length() == 1) {
            return true;
        }
        return switch (token) {
            case "a", "an", "and", "are", "as", "at", "be", "by", "do", "does", "for", "from", "how", "in",
                 "is", "it", "of", "on", "or", "that", "the", "this", "to", "what", "when", "where", "who",
                 "why", "with", "according", "knowledge", "base" -> true;
            default -> false;
        };
    }
}
