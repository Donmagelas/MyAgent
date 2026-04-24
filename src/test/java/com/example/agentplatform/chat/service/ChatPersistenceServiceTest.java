package com.example.agentplatform.chat.service;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.chat.repository.ChatMessageRepository;
import com.example.agentplatform.chat.repository.ConversationRepository;
import com.example.agentplatform.memory.service.MemoryAutomationService;
import com.example.agentplatform.memory.service.ShortTermMemoryService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatPersistenceService 测试。
 * 验证助手消息落库后会更新短期记忆缓存版本并触发长期记忆提炼。
 */
class ChatPersistenceServiceTest {

    @Test
    void shouldTriggerMemoryAutomationAfterAssistantMessageSaved() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        MemoryAutomationService memoryAutomationService = mock(MemoryAutomationService.class);
        ShortTermMemoryService shortTermMemoryService = mock(ShortTermMemoryService.class);
        ChatPersistenceService chatPersistenceService = new ChatPersistenceService(
                conversationRepository,
                chatMessageRepository,
                memoryAutomationService,
                shortTermMemoryService
        );

        ChatMessage assistantMessage = new ChatMessage(
                11L,
                22L,
                33L,
                "assistant",
                "这是本轮回答",
                "TEXT",
                "qwen3.6-plus",
                OffsetDateTime.now()
        );
        when(chatMessageRepository.save(22L, 33L, "assistant", "这是本轮回答", "TEXT", "qwen3.6-plus"))
                .thenReturn(assistantMessage);

        chatPersistenceService.saveAssistantMessage(33L, 22L, "这是本轮回答", "qwen3.6-plus");

        verify(shortTermMemoryService).markConversationUpdated(22L);
        verify(conversationRepository).touchUpdatedAt(22L);
        verify(memoryAutomationService).triggerAfterAssistantMessage(33L, 22L, assistantMessage);
    }
}
