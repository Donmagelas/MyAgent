package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.dto.ConversationDetailResponse;
import com.example.agentplatform.chat.dto.ConversationListItemResponse;
import com.example.agentplatform.chat.dto.ConversationMessageResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话历史查询服务。
 * 向前端提供会话列表和历史消息读取能力。
 */
@Service
public class ChatHistoryService {

    private static final int DEFAULT_CONVERSATION_LIMIT = 50;

    private final ChatPersistenceService chatPersistenceService;

    public ChatHistoryService(ChatPersistenceService chatPersistenceService) {
        this.chatPersistenceService = chatPersistenceService;
    }

    /** 查询当前用户最近的会话列表。 */
    public List<ConversationListItemResponse> listConversations(Long userId) {
        return chatPersistenceService.listConversations(userId, DEFAULT_CONVERSATION_LIMIT).stream()
                .map(summary -> new ConversationListItemResponse(
                        summary.id(),
                        summary.sessionId(),
                        summary.title(),
                        summary.status(),
                        summary.lastMessageRole(),
                        summary.lastMessagePreview(),
                        summary.lastActivityAt()
                ))
                .toList();
    }

    /** 查询当前用户指定会话的完整消息。 */
    public ConversationDetailResponse getConversationDetail(Long userId, Long conversationId) {
        var conversation = chatPersistenceService.getConversation(userId, conversationId);
        var messages = chatPersistenceService.listMessages(userId, conversationId).stream()
                .map(message -> new ConversationMessageResponse(
                        message.id(),
                        message.role(),
                        message.content(),
                        message.messageType(),
                        message.modelName(),
                        message.createdAt()
                ))
                .toList();
        return new ConversationDetailResponse(
                conversation.id(),
                conversation.sessionId(),
                conversation.title(),
                conversation.status(),
                messages
        );
    }
}
