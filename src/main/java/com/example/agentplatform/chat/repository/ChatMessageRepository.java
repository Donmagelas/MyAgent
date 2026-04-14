package com.example.agentplatform.chat.repository;

import com.example.agentplatform.chat.domain.ChatMessage;
import com.example.agentplatform.common.exception.ApplicationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * chat_message 表的写入仓储。
 * Phase 1 阶段只需要追加式保存聊天消息。
 */
@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入一条聊天消息记录，并返回持久化结果视图。 */
    public ChatMessage save(Long conversationId, Long userId, String role, String content, String messageType, String modelName) {
        Long messageId = jdbcTemplate.queryForObject("""
                INSERT INTO chat_message (conversation_id, user_id, role, content, message_type, model_name)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                conversationId,
                userId,
                role,
                content,
                messageType,
                modelName);
        if (messageId == null) {
            throw new ApplicationException("Failed to create chat message");
        }
        return new ChatMessage(
                messageId,
                conversationId,
                userId,
                role,
                content,
                messageType,
                modelName,
                OffsetDateTime.now()
        );
    }

    /** 按会话查询全部消息，按创建时间正序返回。*/
    public List<ChatMessage> findByConversationId(Long conversationId) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, role, content, message_type, model_name, created_at
                FROM chat_message
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """, rowMapper(), conversationId);
    }

    /**
     * 统计指定会话中某个角色的消息条数。
     * 用于自动长期记忆提炼的周期触发判断。
     */
    public int countByConversationIdAndRole(Long conversationId, String role) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM chat_message
                WHERE conversation_id = ?
                  AND role = ?
                """, Integer.class, conversationId, role);
        return count == null ? 0 : count;
    }

    private RowMapper<ChatMessage> rowMapper() {
        return (resultSet, rowNum) -> new ChatMessage(
                resultSet.getLong("id"),
                resultSet.getLong("conversation_id"),
                resultSet.getObject("user_id", Long.class),
                resultSet.getString("role"),
                resultSet.getString("content"),
                resultSet.getString("message_type"),
                resultSet.getString("model_name"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
