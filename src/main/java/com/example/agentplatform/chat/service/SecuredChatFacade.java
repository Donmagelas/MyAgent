package com.example.agentplatform.chat.service;

import com.example.agentplatform.advisor.domain.AdvisorOperation;
import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import com.example.agentplatform.advisor.service.ChatAdvisorExecutor;
import com.example.agentplatform.agent.dto.AgentChatRequest;
import com.example.agentplatform.agent.service.AgentStreamService;
import com.example.agentplatform.auth.service.AuthenticatedUserAccessor;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 带安全校验的聊天门面服务。
 * 当前仅保留前端主链使用的统一 Agent 流式入口。
 */
@Service
public class SecuredChatFacade {

    private final ChatAdvisorExecutor chatAdvisorExecutor;
    private final AuthenticatedUserAccessor authenticatedUserAccessor;
    private final AgentStreamService agentStreamService;

    public SecuredChatFacade(
            ChatAdvisorExecutor chatAdvisorExecutor,
            AuthenticatedUserAccessor authenticatedUserAccessor,
            AgentStreamService agentStreamService
    ) {
        this.chatAdvisorExecutor = chatAdvisorExecutor;
        this.authenticatedUserAccessor = authenticatedUserAccessor;
        this.agentStreamService = agentStreamService;
    }

    /**
     * 执行带守卫的统一流式聊天请求。
     * 所有公开聊天请求都会进入 Agent 流式执行链。
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> smartStream(ChatAskRequest request, Authentication authentication) {
        authenticatedUserAccessor.requireUserId(authentication);
        chatAdvisorExecutor.execute(new ChatAdvisorContext(
                AdvisorOperation.AGENT_STREAM,
                request.message(),
                authentication
        ));
        return agentStreamService.stream(toAgentRequest(request), authentication);
    }

    private AgentChatRequest toAgentRequest(ChatAskRequest request) {
        return new AgentChatRequest(
                request.sessionId(),
                request.message(),
                request.agentMode(),
                request.agentMaxSteps(),
                request.preferKnowledgeRetrieval(),
                request.knowledgeDocumentHint()
        );
    }
}
