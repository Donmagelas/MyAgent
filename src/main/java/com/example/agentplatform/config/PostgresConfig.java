package com.example.agentplatform.config;

import org.springframework.context.annotation.Configuration;

/**
 * PostgreSQL 扩展配置入口。
 * 当前 Phase 1 阶段主要由 Spring Boot JDBC 自动配置满足需求。
 */
@Configuration
public class PostgresConfig {
    // 预留给后续 JDBC、事务或 SQL 方言相关定制。
}
