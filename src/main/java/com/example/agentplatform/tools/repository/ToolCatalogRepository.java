package com.example.agentplatform.tools.repository;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.tools.domain.PlatformToolDefinition;
import com.example.agentplatform.tools.domain.ToolCatalogEntry;
import com.example.agentplatform.tools.domain.ToolRiskLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工具目录仓储。
 * 负责 tool_definition 表的读写和基础检索。
 */
@Repository
public class ToolCatalogRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public ToolCatalogRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据本地注册工具定义做目录同步。
     */
    public void upsert(PlatformToolDefinition definition) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("toolName", definition.name())
                .addValue("implementationKey", definition.implementationKey())
                .addValue("displayName", definition.displayName())
                .addValue("description", definition.description())
                .addValue("inputSchemaJson", definition.inputSchema())
                .addValue("enabled", definition.enabled())
                .addValue("readOnly", definition.readOnly())
                .addValue("mutatesState", definition.mutatesState())
                .addValue("dangerous", definition.dangerous())
                .addValue("returnDirect", definition.returnDirect())
                .addValue("requiresApproval", definition.requiresApproval())
                .addValue("timeoutMillis", definition.timeoutMillis())
                .addValue("riskLevel", definition.riskLevel().name())
                .addValue("allowedRolesJson", toJson(definition.allowedRoles()))
                .addValue("tagsJson", toJson(definition.tags()))
                .addValue("scopesJson", toJson(definition.scopes()))
                .addValue("metadataJson", toJson(Map.of()));

        namedParameterJdbcTemplate.update("""
                INSERT INTO tool_definition (
                    tool_name,
                    implementation_key,
                    display_name,
                    description,
                    input_schema_json,
                    enabled,
                    read_only,
                    mutates_state,
                    dangerous,
                    return_direct,
                    requires_approval,
                    timeout_ms,
                    risk_level,
                    allowed_roles,
                    tags,
                    scopes,
                    metadata_json
                )
                VALUES (
                    :toolName,
                    :implementationKey,
                    :displayName,
                    :description,
                    CAST(:inputSchemaJson AS jsonb),
                    :enabled,
                    :readOnly,
                    :mutatesState,
                    :dangerous,
                    :returnDirect,
                    :requiresApproval,
                    :timeoutMillis,
                    :riskLevel,
                    CAST(:allowedRolesJson AS jsonb),
                    CAST(:tagsJson AS jsonb),
                    CAST(:scopesJson AS jsonb),
                    CAST(:metadataJson AS jsonb)
                )
                ON CONFLICT (tool_name) DO UPDATE
                SET implementation_key = EXCLUDED.implementation_key,
                    display_name = EXCLUDED.display_name,
                    description = EXCLUDED.description,
                    input_schema_json = EXCLUDED.input_schema_json,
                    enabled = EXCLUDED.enabled,
                    read_only = EXCLUDED.read_only,
                    mutates_state = EXCLUDED.mutates_state,
                    dangerous = EXCLUDED.dangerous,
                    return_direct = EXCLUDED.return_direct,
                    requires_approval = EXCLUDED.requires_approval,
                    timeout_ms = EXCLUDED.timeout_ms,
                    risk_level = EXCLUDED.risk_level,
                    allowed_roles = EXCLUDED.allowed_roles,
                    tags = EXCLUDED.tags,
                    scopes = EXCLUDED.scopes,
                    updated_at = CURRENT_TIMESTAMP
                """, parameters);
    }

    /**
     * 查询所有启用中的工具目录项。
     */
    public List<ToolCatalogEntry> findAllEnabled(int limit) {
        return namedParameterJdbcTemplate.query("""
                SELECT id,
                       tool_name,
                       implementation_key,
                       display_name,
                       description,
                       input_schema_json,
                       enabled,
                       read_only,
                       mutates_state,
                       dangerous,
                       return_direct,
                       requires_approval,
                       timeout_ms,
                       risk_level,
                       allowed_roles,
                       tags,
                       scopes,
                       metadata_json,
                       created_at,
                       updated_at
                FROM tool_definition
                WHERE enabled = TRUE
                ORDER BY updated_at DESC, id DESC
                LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), rowMapper());
    }

    /**
     * 按问题文本粗粒度搜索候选工具。
     * 当前第一版先用 ILIKE，后续再增强到 FTS 或向量召回。
     */
    public List<ToolCatalogEntry> searchEnabled(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return findAllEnabled(limit);
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("query", "%" + normalizedQuery + "%")
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.query("""
                SELECT id,
                       tool_name,
                       implementation_key,
                       display_name,
                       description,
                       input_schema_json,
                       enabled,
                       read_only,
                       mutates_state,
                       dangerous,
                       return_direct,
                       requires_approval,
                       timeout_ms,
                       risk_level,
                       allowed_roles,
                       tags,
                       scopes,
                       metadata_json,
                       created_at,
                       updated_at
                FROM tool_definition
                WHERE enabled = TRUE
                  AND (
                       tool_name ILIKE :query
                    OR implementation_key ILIKE :query
                    OR display_name ILIKE :query
                    OR description ILIKE :query
                    OR CAST(tags AS TEXT) ILIKE :query
                  )
                ORDER BY updated_at DESC, id DESC
                LIMIT :limit
                """, parameters, rowMapper());
    }

    private RowMapper<ToolCatalogEntry> rowMapper() {
        return (resultSet, rowNum) -> new ToolCatalogEntry(
                resultSet.getLong("id"),
                resultSet.getString("tool_name"),
                resultSet.getString("implementation_key"),
                resultSet.getString("display_name"),
                resultSet.getString("description"),
                resultSet.getString("input_schema_json"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("read_only"),
                resultSet.getBoolean("mutates_state"),
                resultSet.getBoolean("dangerous"),
                resultSet.getBoolean("return_direct"),
                resultSet.getBoolean("requires_approval"),
                resultSet.getLong("timeout_ms"),
                ToolRiskLevel.valueOf(resultSet.getString("risk_level")),
                readStringSet(resultSet, "allowed_roles"),
                readStringList(resultSet, "tags"),
                readStringList(resultSet, "scopes"),
                readMetadata(resultSet),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private java.util.Set<String> readStringSet(ResultSet resultSet, String columnName) throws SQLException {
        return java.util.Set.copyOf(readStringList(resultSet, columnName));
    }

    private List<String> readStringList(ResultSet resultSet, String columnName) throws SQLException {
        try {
            String value = resultSet.getString(columnName);
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("反序列化工具目录字段失败: " + columnName, exception);
        }
    }

    private Map<String, Object> readMetadata(ResultSet resultSet) throws SQLException {
        try {
            String value = resultSet.getString("metadata_json");
            if (value == null || value.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(value, MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("反序列化工具目录元数据失败", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new ApplicationException("序列化工具目录数据失败", exception);
        }
    }
}
