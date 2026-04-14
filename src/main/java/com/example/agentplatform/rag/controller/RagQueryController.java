package com.example.agentplatform.rag.controller;

import com.example.agentplatform.rag.domain.RetrievedChunk;
import com.example.agentplatform.rag.dto.RetrievalResponse;
import com.example.agentplatform.rag.service.RetrievalService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 检索查询控制器。
 * 提供独立接口，用于在不走聊天链路时验证检索结果。
 */
@Validated
@RestController
@RequestMapping("/api/rag")
public class RagQueryController {

    private final RetrievalService retrievalService;

    public RagQueryController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /** 返回当前查询对应的合并检索结果。 */
    @GetMapping("/retrieve")
    public Mono<RetrievalResponse> retrieve(@RequestParam("q") @NotBlank String query) {
        return Mono.fromCallable(() -> {
                    List<RetrievedChunk> chunks = retrievalService.retrieve(query);
                    List<RetrievalResponse.RetrievedChunkItem> items = chunks.stream()
                            .map(chunk -> new RetrievalResponse.RetrievedChunkItem(
                                    chunk.chunkId(),
                                    chunk.documentId(),
                                    chunk.documentTitle(),
                                    chunk.chunkTitle(),
                                    chunk.sectionPath(),
                                    chunk.jsonPath(),
                                    chunk.chunkIndex(),
                                    chunk.content(),
                                    chunk.score(),
                                    chunk.retrievalType()
                            ))
                            .toList();
                    return new RetrievalResponse(query, items);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}