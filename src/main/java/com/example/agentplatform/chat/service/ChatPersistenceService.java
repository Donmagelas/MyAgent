package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.domain.ConversationSummary;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.repository.ChatMessageRepository;
import com.example.agentplatform.chat.repository.ConversationRepository;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.memory.service.MemoryAutomationService;
import com.example.agentplatform.memory.service.ShortTermMemoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 聊天持久化服务。
 * 集中处理会话查询、会话列表和消息持久化逻辑。
 */
@Service
public class ChatPersistenceService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryAutomationService memoryAutomationService;
    private final ShortTermMemoryService shortTermMemoryService;

    public ChatPersistenceService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            MemoryAutomationService memoryAutomationService,
            ShortTermMemoryService shortTermMemoryService
    ) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memoryAutomationService = memoryAutomationService;
        this.shortTermMemoryService = shortTermMemoryService;
    }

    /**
     * 查找或创建与当前会话标识关联的会话。
     */
    public Conversation getOrCreateConversation(Long userId, ChatAskRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "sess_" + UUID.randomUUID().toString().replace("-", "")
                : request.sessionId();
        return conversationRepository.findBySessionId(sessionId)
                .map(conversation -> validateConversationOwner(conversation, userId))
                .orElseGet(() -> conversationRepository.save(userId, sessionId, buildTitle(request.message())));
    }

    /**
     * 持久化一条用户消息，并刷新会话更新时间。
     */
    public ChatMessage saveUserMessage(Long userId, Long conversationId, String content) {
        ChatMessage message = chatMessageRepository.save(conversationId, userId, "user", content, "TEXT", null);
        shortTermMemoryService.markConversationUpdated(conversationId);
        conversationRepository.touchUpdatedAt(conversationId);
        return message;
    }

    /**
     * 持久化一条助手消息，并刷新会话更新时间。
     */
    public ChatMessage saveAssistantMessage(Long userId, Long conversationId, String content, String modelName) {
        ChatMessage message = chatMessageRepository.save(conversationId, userId, "assistant", content, "TEXT", modelName);
        shortTermMemoryService.markConversationUpdated(conversationId);
        conversationRepository.touchUpdatedAt(conversationId);
        // 助手消息落库后异步触发长期记忆提炼，避免阻塞主对话链路。
        memoryAutomationService.triggerAfterAssistantMessage(userId, conversationId, message);
        return message;
    }

    /**
     * 列出当前用户的会话摘要。
     */
    public List<ConversationSummary> listConversations(Long userId, int limit) {
        return conversationRepository.listByUserId(userId, limit);
    }

    /**
     * 加载当前用户指定的会话。
     */
    public Conversation getConversation(Long userId, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ApplicationException("当前会话不存在或不属于当前登录用户"));
    }

    /**
     * 加载当前用户指定会话的完整消息历史。
     */
    public List<ChatMessage> listMessages(Long userId, Long conversationId) {
        Conversation conversation = getConversation(userId, conversationId);
        return chatMessageRepository.findByConversationId(conversation.id());
    }

    private String buildTitle(String message) {
        int end = Math.min(30, message.length());
        return message.substring(0, end);
    }

    private Conversation validateConversationOwner(Conversation conversation, Long userId) {
        if (conversation.userId() != null && !conversation.userId().equals(userId)) {
            throw new ApplicationException("当前会话不属于当前登录用户");
        }
        return conversation;
    }
}
