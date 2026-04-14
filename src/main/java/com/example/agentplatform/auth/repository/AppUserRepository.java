package com.example.agentplatform.auth.repository;

import com.example.agentplatform.auth.domain.AppUser;
import com.example.agentplatform.common.exception.ApplicationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储。
 * 负责 app_user 与 app_user_role 两张表的读写。
 */
@Repository
public class AppUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 按用户名查询用户，用户名按不区分大小写处理。 */
    public Optional<AppUser> findByUsername(String username) {
        List<AppUser> users = jdbcTemplate.query("""
                SELECT id, username, password_hash, status, created_at, updated_at
                FROM app_user
                WHERE LOWER(username) = LOWER(?)
                """, baseRowMapper(), username);
        return users.stream().findFirst().map(this::attachRoles);
    }

    /** 按主键查询用户。 */
    public Optional<AppUser> findById(Long userId) {
        List<AppUser> users = jdbcTemplate.query("""
                SELECT id, username, password_hash, status, created_at, updated_at
                FROM app_user
                WHERE id = ?
                """, baseRowMapper(), userId);
        return users.stream().findFirst().map(this::attachRoles);
    }

    /** 创建新用户并写入角色。 */
    @Transactional
    public AppUser create(String username, String passwordHash, List<String> roles) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, status)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, username, passwordHash, "ACTIVE");
        if (userId == null) {
            throw new ApplicationException("创建用户失败");
        }
        replaceRoles(userId, roles);
        return findById(userId).orElseThrow(() -> new ApplicationException("创建用户后查询失败"));
    }

    /** 用于启动期的账号引导，已存在时会同步密码和角色。 */
    @Transactional
    public void upsertBootstrapUser(String username, String passwordHash, List<String> roles) {
        Optional<AppUser> existing = findByUsername(username);
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE app_user
                    SET password_hash = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, passwordHash, "ACTIVE", existing.get().id());
            replaceRoles(existing.get().id(), roles);
            return;
        }
        create(username, passwordHash, roles);
    }

    private AppUser attachRoles(AppUser user) {
        return new AppUser(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.status(),
                user.createdAt(),
                user.updatedAt(),
                findRolesByUserId(user.id())
        );
    }

    private List<String> findRolesByUserId(Long userId) {
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT role_name
                FROM app_user_role
                WHERE user_id = ?
                ORDER BY role_name
                """, String.class, userId);
        return Collections.unmodifiableList(roles);
    }

    private void replaceRoles(Long userId, List<String> roles) {
        jdbcTemplate.update("DELETE FROM app_user_role WHERE user_id = ?", userId);
        for (String role : roles) {
            jdbcTemplate.update("""
                    INSERT INTO app_user_role (user_id, role_name)
                    VALUES (?, ?)
                    """, userId, role);
        }
    }

    private RowMapper<AppUser> baseRowMapper() {
        return (resultSet, rowNum) -> new AppUser(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                List.of()
        );
    }
}
