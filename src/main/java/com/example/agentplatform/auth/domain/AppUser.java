package com.example.agentplatform.auth.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 应用用户聚合。
 * 包含用户基础信息、状态以及当前持有的角色集合。
 */
public record AppUser(
        Long id,
        String username,
        String passwordHash,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> roles
) {
}
