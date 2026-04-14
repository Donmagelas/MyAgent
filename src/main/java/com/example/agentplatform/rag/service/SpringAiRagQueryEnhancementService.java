package com.example.agentplatform.rag.service;

import com.example.agentplatform.config.RagQueryEnhancementProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 查询增强服务。
 * 统一管理预检索阶段的查询改写、压缩、翻译和多查询扩展能力。
 */
@Service
public class SpringAiRagQueryEnhancementService {

    private final ChatModel chatModel;
    private final RagQueryEnhancementProperties queryEnhancementProperties;
    private final List<QueryTransformer> queryTransformers;
    private final QueryExpander queryExpander;

    public SpringAiRagQueryEnhancementService(
            ChatModel chatModel,
            RagQueryEnhancementProperties queryEnhancementProperties
    ) {
        this.chatModel = chatModel;
        this.queryEnhancementProperties = queryEnhancementProperties;
        this.queryTransformers = List.copyOf(buildQueryTransformers());
        this.queryExpander = buildQueryExpander();
    }

    /**
     * 顺序执行全部查询转换器。
     */
    public Query transform(Query query) {
        Query current = query;
        for (QueryTransformer queryTransformer : queryTransformers) {
            current = queryTransformer.transform(current);
        }
        return current;
    }

    /**
     * 执行多查询扩展；若未启用则返回原始查询。
     */
    public List<Query> expand(Query query) {
        if (queryExpander == null) {
            return List.of(query);
        }
        List<Query> expandedQueries = queryExpander.expand(query);
        if (expandedQueries == null || expandedQueries.isEmpty()) {
            return List.of(query);
        }
        return expandedQueries;
    }

    /**
     * 返回当前已启用的查询转换器。
     */
    public List<QueryTransformer> getQueryTransformers() {
        return queryTransformers;
    }

    /**
     * 返回当前已启用的多查询扩展器。
     */
    public QueryExpander getQueryExpander() {
        return queryExpander;
    }

    private List<QueryTransformer> buildQueryTransformers() {
        List<QueryTransformer> transformers = new ArrayList<>();

        RagQueryEnhancementProperties.TranslationProperties translation = queryEnhancementProperties.translation();
        if (translation != null && translation.enabled()) {
            TranslationQueryTransformer.Builder builder = TranslationQueryTransformer.builder()
                    .chatClientBuilder(newChatClientBuilder());
            if (StringUtils.hasText(translation.targetLanguage())) {
                builder.targetLanguage(translation.targetLanguage().trim());
            }
            transformers.add(builder.build());
        }

        RagQueryEnhancementProperties.RewriteProperties rewrite = queryEnhancementProperties.rewrite();
        if (rewrite != null && rewrite.enabled()) {
            RewriteQueryTransformer.Builder builder = RewriteQueryTransformer.builder()
                    .chatClientBuilder(newChatClientBuilder());
            if (StringUtils.hasText(rewrite.targetSearchSystem())) {
                builder.targetSearchSystem(rewrite.targetSearchSystem().trim());
            }
            transformers.add(builder.build());
        }

        RagQueryEnhancementProperties.CompressionProperties compression = queryEnhancementProperties.compression();
        if (compression != null && compression.enabled()) {
            transformers.add(CompressionQueryTransformer.builder()
                    .chatClientBuilder(newChatClientBuilder())
                    .build());
        }

        return transformers;
    }

    private QueryExpander buildQueryExpander() {
        RagQueryEnhancementProperties.MultiQueryProperties multiQuery = queryEnhancementProperties.multiQuery();
        if (multiQuery == null || !multiQuery.enabled()) {
            return null;
        }
        return MultiQueryExpander.builder()
                .chatClientBuilder(newChatClientBuilder())
                .includeOriginal(multiQuery.includeOriginal())
                .numberOfQueries(multiQuery.queryCount())
                .build();
    }

    private ChatClient.Builder newChatClientBuilder() {
        return ChatClient.builder(chatModel);
    }
}
