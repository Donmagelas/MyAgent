package com.example.agentplatform.chat.repository;

import com.example.agentplatform.chat.domain.Conversation;
import com.example.agentplatform.chat.domain.ConversationSummary;
import com.example.agentplatform.common.exception.ApplicationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * conversation 表的仓储。
 * 支持按 sessionId 查询会话以及创建新的激活会话。
 */
@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按 sessionId 查询对应会话。 */
    public Optional<Conversation> findBySessionId(String sessionId) {
        List<Conversation> results = jdbcTemplate.query("""
                SELECT id, user_id, session_id, title, status, created_at, updated_at
                FROM conversation
                WHERE session_id = ?
                """, rowMapper(), sessionId);
        return results.stream().findFirst();
    }

    /** 创建一个新的激活会话。 */
    public Conversation save(Long userId, String sessionId, String title) {
        Long conversationId = jdbcTemplate.queryForObject("""
                INSERT INTO conversation (user_id, session_id, title, status)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                userId,
                sessionId,
                title,
                "ACTIVE");
        if (conversationId == null) {
            throw new ApplicationException("Failed to create conversation");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new Conversation(conversationId, userId, sessionId, title, "ACTIVE", now, now);
    }

    /** 按用户和会话 ID 查询单条会话。*/
    public Optional<Conversation> findByIdAndUserId(Long conversationId, Long userId) {
        List<Conversation> results = jdbcTemplate.query("""
                SELECT id, user_id, session_id, title, status, created_at, updated_at
                FROM conversation
                WHERE id = ? AND user_id = ?
                """, rowMapper(), conversationId, userId);
        return results.stream().findFirst();
    }

    /** 按最近活动时间倒序列出当前用户的会话摘要。*/
    public List<ConversationSummary> listByUserId(Long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT c.id,
                       c.session_id,
                       c.title,
                       c.status,
                       c.created_at,
                       c.updated_at,
                       lm.role AS last_message_role,
                       lm.content AS last_message_preview,
                       COALESCE(lm.created_at, c.updated_at) AS last_activity_at
                FROM conversation c
                LEFT JOIN LATERAL (
                    SELECT role, content, created_at
                    FROM chat_message
                    WHERE conversation_id = c.id
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                ) lm ON TRUE
                WHERE c.user_id = ?
                ORDER BY COALESCE(lm.created_at, c.updated_at) DESC, c.id DESC
                LIMIT ?
                """, conversationSummaryRowMapper(), userId, limit);
    }

    /** 在会话追加消息后刷新 updated_at，便于列表按最新活动排序。*/
    public void touchUpdatedAt(Long conversationId) {
        jdbcTemplate.update("""
                UPDATE conversation
                SET updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, conversationId);
    }

    private RowMapper<Conversation> rowMapper() {
        return (resultSet, rowNum) -> new Conversation(
                resultSet.getLong("id"),
                resultSet.getObject("user_id", Long.class),
                resultSet.getString("session_id"),
                resultSet.getString("title"),
                resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private RowMapper<ConversationSummary> conversationSummaryRowMapper() {
        return (resultSet, rowNum) -> new ConversationSummary(
                resultSet.getLong("id"),
                resultSet.getString("session_id"),
                resultSet.getString("title"),
                resultSet.getString("status"),
                resultSet.getString("last_message_role"),
                resultSet.getString("last_message_preview"),
                resultSet.getObject("last_activity_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
