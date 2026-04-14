package com.example.agentplatform.tasks.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务依赖仓储。
 * 负责维护任务之间的阻塞关系。
 */
@Repository
public class TaskDependencyRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TaskDependencyRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /** 批量写入任务依赖。 */
    public void saveAll(Long taskId, List<Long> blockedByTaskIds) {
        if (blockedByTaskIds == null || blockedByTaskIds.isEmpty()) {
            return;
        }
        for (Long blockedByTaskId : blockedByTaskIds) {
            namedParameterJdbcTemplate.update("""
                    INSERT INTO task_dependency (task_id, blocked_by_task_id)
                    VALUES (:taskId, :blockedByTaskId)
                    ON CONFLICT DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("taskId", taskId)
                            .addValue("blockedByTaskId", blockedByTaskId));
        }
    }

    /** 查询某个任务被哪些任务阻塞。 */
    public List<Long> findBlockedByTaskIds(Long taskId) {
        return namedParameterJdbcTemplate.queryForList("""
                SELECT blocked_by_task_id
                FROM task_dependency
                WHERE task_id = :taskId
                ORDER BY blocked_by_task_id
                """,
                new MapSqlParameterSource("taskId", taskId),
                Long.class);
    }

    /** 查询某个任务解锁了哪些下游任务。 */
    public List<Long> findDependentTaskIds(Long blockedByTaskId) {
        return namedParameterJdbcTemplate.queryForList("""
                SELECT task_id
                FROM task_dependency
                WHERE blocked_by_task_id = :blockedByTaskId
                ORDER BY task_id
                """,
                new MapSqlParameterSource("blockedByTaskId", blockedByTaskId),
                Long.class);
    }

    /** 统计某个任务仍未满足的依赖数量。 */
    public int countIncompleteDependencies(Long taskId) {
        Integer count = namedParameterJdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM task_dependency td
                JOIN task_record tr ON tr.id = td.blocked_by_task_id
                WHERE td.task_id = :taskId
                  AND tr.status <> 'COMPLETED'
                """,
                new MapSqlParameterSource("taskId", taskId),
                Integer.class);
        return count == null ? 0 : count;
    }
}
