package com.example.agentplatform.memory.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.memory.advisor.MemoryContextAdvisor;
import com.example.agentplatform.memory.domain.LongTermMemory;
import com.example.agentplatform.memory.domain.MemoryContext;
import com.example.agentplatform.memory.domain.RetrievedMemorySummary;
import com.example.agentplatform.memory.dto.LongTermMemoryCreateRequest;
import com.example.agentplatform.memory.dto.LongTermMemoryQueryRequest;
import com.example.agentplatform.memory.dto.LongTermMemorySearchRequest;
import com.example.agentplatform.memory.dto.MemoryContextPreviewRequest;
import com.example.agentplatform.memory.dto.MemorySummaryCreateRequest;
import com.example.agentplatform.memory.dto.MemorySummarySearchRequest;
import com.example.agentplatform.memory.dto.MemorySummaryWriteRequest;
import com.example.agentplatform.memory.service.LongTermMemoryService;
import com.example.agentplatform.memory.service.MemorySummaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 记忆接口控制器。
 * 提供长期记忆、摘要和记忆上下文预览的最小 API。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final LongTermMemoryService longTermMemoryService;
    private final MemorySummaryService memorySummaryService;
    private final MemoryContextAdvisor memoryContextAdvisor;

    public MemoryController(
            AuthenticatedUserAccessor authenticatedUserAccessor,
            LongTermMemoryService longTermMemoryService,
            MemorySummaryService memorySummaryService,
            MemoryContextAdvisor memoryContextAdvisor
    ) {
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.longTermMemoryService = longTermMemoryService;
        this.memorySummaryService = memorySummaryService;
        this.memoryContextAdvisor = memoryContextAdvisor;
    }

    /** 创建一条长期记忆。 */
    @PostMapping("/long-term")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LongTermMemory> createLongTermMemory(
            @Valid @RequestBody LongTermMemoryCreateRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> longTermMemoryService.save(new com.example.agentplatform.memory.dto.LongTermMemoryWriteRequest(
                        authenticatedUserAccessor.requireUserId(authentication),
                        request.conversationId(),
                        request.memoryType(),
                        request.subject(),
                        request.content(),
                        request.importance(),
                        request.active(),
                        request.sourceType(),
                        request.sourceRef(),
                        request.metadata()
                )))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 查询当前登录用户的长期记忆。 */
    @PostMapping("/long-term/search")
    public Mono<List<LongTermMemory>> searchLongTermMemories(
            @RequestBody LongTermMemorySearchRequest request,
            Authentication authentication
    ) {
                return Mono.fromCallable(() -> longTermMemoryService.query(new LongTermMemoryQueryRequest(
                        authenticatedUserAccessor.requireUserId(authentication),
                        request.memoryTypes(),
                        request.subject(),
                        request.minImportance(),
                        request.active(),
                        request.limit(),
                        request.metadataFilter()
                )))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 创建一条记忆摘要。 */
    @PostMapping("/summary")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<com.example.agentplatform.memory.domain.MemorySummary> createSummary(
            @Valid @RequestBody MemorySummaryCreateRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> memorySummaryService.save(new MemorySummaryWriteRequest(
                        authenticatedUserAccessor.requireUserId(authentication),
                        request.longTermMemoryId(),
                        request.conversationId(),
                        request.summaryText(),
                        request.importance(),
                        request.active(),
                        request.sourceType(),
                        request.sourceRef(),
                        request.metadata()
                )))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 按当前问题检索相关记忆摘要。 */
    @PostMapping("/summary/search")
    public Mono<List<RetrievedMemorySummary>> searchSummaries(
            @Valid @RequestBody MemorySummarySearchRequest request,
            Authentication authentication
    ) {
                return Mono.fromCallable(() -> memorySummaryService.search(
                        authenticatedUserAccessor.requireUserId(authentication),
                        request.question(),
                        request.topK() == null ? 5 : request.topK(),
                        request.metadataFilter()
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 预览当前问题会组装出的统一记忆上下文。 */
    @PostMapping("/context")
    public Mono<MemoryContext> previewContext(
            @Valid @RequestBody MemoryContextPreviewRequest request,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> memoryContextAdvisor.buildContext(
                        authenticatedUserAccessor.requireUserId(authentication),
                        request.conversationId(),
                        request.question()
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
