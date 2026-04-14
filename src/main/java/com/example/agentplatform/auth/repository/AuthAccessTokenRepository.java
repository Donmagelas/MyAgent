package com.example.agentplatform.auth.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 访问令牌仓储。
 * 负责登录后发放的 Bearer Token 哈希持久化。
 */
@Repository
public class AuthAccessTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthAccessTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 保存新的访问令牌。 */
    public void save(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_access_token (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, tokenHash, expiresAt);
    }

    /** 查询当前仍然有效且未撤销的令牌所属用户。 */
    public Optional<Long> findActiveUserIdByTokenHash(String tokenHash, OffsetDateTime now) {
        List<Long> userIds = jdbcTemplate.queryForList("""
                SELECT user_id
                FROM auth_access_token
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, tokenHash, now);
        return userIds.stream().findFirst();
    }

    /** 同一用户重新登录时撤销旧令牌，避免长期累积。 */
    @Transactional
    public void revokeActiveTokensByUserId(Long userId, OffsetDateTime now) {
        jdbcTemplate.update("""
                UPDATE auth_access_token
                SET revoked_at = ?
                WHERE user_id = ?
                  AND revoked_at IS NULL
                """, now, userId);
    }
}
