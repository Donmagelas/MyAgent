package com.example.agentplatform.memory.repository;

import com.example.agentplatform.memory.domain.RecentConversationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 短期记忆仓储。
 * 直接基于 conversation 与 chat_message 读取当前会话窗口。
 */
@Repository
public class ShortTermMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShortTermMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按会话读取最近 N 条消息，并按时间正序返回。 */
    public List<RecentConversationMessage> findRecentMessages(Long userId, Long conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT recent.id,
                       recent.conversation_id,
                       recent.user_id,
                       recent.role,
                       recent.content,
                       recent.message_type,
                       recent.model_name,
                       recent.created_at
                FROM (
                    SELECT cm.id,
                           cm.conversation_id,
                           cm.user_id,
                           cm.role,
                           cm.content,
                           cm.message_type,
                           cm.model_name,
                           cm.created_at
                    FROM chat_message cm
                    JOIN conversation c ON c.id = cm.conversation_id
                    WHERE cm.conversation_id = ?
                      AND (? IS NULL OR c.user_id = ?)
                    ORDER BY cm.created_at DESC
                    LIMIT ?
                ) recent
                ORDER BY recent.created_at ASC
                """,
                rowMapper(),
                conversationId,
                userId,
                userId,
                limit);
    }

    private RowMapper<RecentConversationMessage> rowMapper() {
        return (resultSet, rowNum) -> new RecentConversationMessage(
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
