package com.example.agentplatform.chat.controller;

import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.chat.dto.ConversationDetailResponse;
import com.example.agentplatform.chat.dto.ConversationListItemResponse;
import com.example.agentplatform.chat.service.ChatHistoryService;
import com.example.agentplatform.chat.service.SecuredChatFacade;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 聊天接口控制器。
 * 当前仅保留前端主链实际使用的流式聊天与会话历史接口。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final SecuredChatFacade securedChatFacade;
    private final ChatHistoryService chatHistoryService;
    private final AuthenticatedUserAccessor authenticatedUserAccessor;

    public ChatController(
            SecuredChatFacade securedChatFacade,
            ChatHistoryService chatHistoryService,
            AuthenticatedUserAccessor authenticatedUserAccessor
    ) {
        this.securedChatFacade = securedChatFacade;
        this.chatHistoryService = chatHistoryService;
        this.authenticatedUserAccessor = authenticatedUserAccessor;
    }

    /**
     * 主链流式聊天接口。
     * 所有前端对话统一从这里进入 Agent 流式执行链。
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
            @Valid @RequestBody ChatAskRequest request,
            Authentication authentication
    ) {
        return securedChatFacade.smartStream(request, authentication);
    }

    /**
     * 查询当前登录用户的会话列表。
     */
    @GetMapping("/conversations")
    public Mono<java.util.List<ConversationListItemResponse>> conversations(Authentication authentication) {
        return Mono.fromCallable(() -> chatHistoryService.listConversations(
                        authenticatedUserAccessor.requireUserId(authentication)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询当前登录用户指定会话的历史消息。
     */
    @GetMapping("/conversations/{conversationId}")
    public Mono<ConversationDetailResponse> conversationDetail(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        return Mono.fromCallable(() -> chatHistoryService.getConversationDetail(
                        authenticatedUserAccessor.requireUserId(authentication),
                        conversationId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
