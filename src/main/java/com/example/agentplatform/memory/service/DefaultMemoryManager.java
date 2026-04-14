package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.MemoryProperties;
import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.memory.domain.MemoryQuery;
import com.example.agentplatform.memory.domain.MemoryQueryIntent;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认记忆管理器。
 * 统一调度三层记忆查询，并按固定顺序组装上下文。
 */
@Service
public class DefaultMemoryManager implements MemoryManager {

    private final MemoryProperties memoryProperties;
    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final MemorySummaryService memorySummaryService;
    private final MemoryContextAssembler memoryContextAssembler;
    private final StructuredLongTermMemoryQueryService structuredLongTermMemoryQueryService;

    public DefaultMemoryManager(
            MemoryProperties memoryProperties,
            ShortTermMemoryService shortTermMemoryService,
            LongTermMemoryService longTermMemoryService,
            MemorySummaryService memorySummaryService,
            MemoryContextAssembler memoryContextAssembler,
            StructuredLongTermMemoryQueryService structuredLongTermMemoryQueryService
    ) {
        this.memoryProperties = memoryProperties;
        this.shortTermMemoryService = shortTermMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.memorySummaryService = memorySummaryService;
        this.memoryContextAssembler = memoryContextAssembler;
        this.structuredLongTermMemoryQueryService = structuredLongTermMemoryQueryService;
    }

    @Override
    public MemoryContext buildContext(MemoryQuery query) {
        List<RecentConversationMessage> recentMessages = query.conversationId() == null
                ? List.of()
                : shortTermMemoryService.loadRecentMessages(
                        query.userId(),
                        query.conversationId(),
                        query.recentMessageLimit() == null ? memoryProperties.shortTermWindowSize() : query.recentMessageLimit()
                );

        MemoryQueryIntent memoryQueryIntent = buildMemoryQueryIntent(query);
        LongTermMemoryQueryRequest structuredQuery = buildLongTermMemoryQuery(query, memoryQueryIntent);
        List<LongTermMemory> stableFacts = longTermMemoryService.query(structuredQuery);

        List<RetrievedMemorySummary> recalledSummaries = memorySummaryService.search(
                query.userId(),
                query.question(),
                query.summaryTopK() == null ? memoryProperties.summaryTopK() : query.summaryTopK(),
                memoryQueryIntent.metadataFilter()
        );

        return memoryContextAssembler.assemble(stableFacts, recalledSummaries, recentMessages);
    }

    private MemoryQueryIntent buildMemoryQueryIntent(MemoryQuery query) {
        if (query.memoryTypes() == null || query.memoryTypes().isEmpty()) {
            return structuredLongTermMemoryQueryService.parseIntent(query.question());
        }
        return new MemoryQueryIntent(
                query.memoryTypes(),
                null,
                null,
                null
        );
    }

    private LongTermMemoryQueryRequest buildLongTermMemoryQuery(MemoryQuery query, MemoryQueryIntent memoryQueryIntent) {
        if (query.memoryTypes() == null || query.memoryTypes().isEmpty()) {
            return structuredLongTermMemoryQueryService.build(
                    query.userId(),
                    query.question(),
                    query.stableFactLimit() == null ? memoryProperties.stableFactLimit() : query.stableFactLimit()
            );
        }
        return new LongTermMemoryQueryRequest(
                query.userId(),
                memoryQueryIntent.memoryTypes(),
                null,
                null,
                true,
                query.stableFactLimit() == null ? memoryProperties.stableFactLimit() : query.stableFactLimit(),
                memoryQueryIntent.metadataFilter()
        );
    }
}
