package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class TaskResponseBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun batchInsert(
        responses: List<TaskResponseMessage>,
        status: String = "RECEIVED",
    ) {
        val sql =
            """
            INSERT INTO task_response (workflow_id, task_id, sequence, payload, status, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?, now())
            ON CONFLICT (workflow_id, task_id, sequence) DO NOTHING
            """.trimIndent()

        jdbcTemplate.batchUpdate(sql, responses, 1000) { ps, r ->
            ps.setLong(1, r.workflowId)
            ps.setLong(2, r.taskId)
            ps.setInt(3, r.sequence)
            ps.setString(4, r.payload)
            ps.setString(5, status)
        }
    }
}
