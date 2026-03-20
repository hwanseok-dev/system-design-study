package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.domain.response.TaskResponse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface TaskResponseRepository : JpaRepository<TaskResponse, Long> {
    @Modifying
    @Transactional
    @Query(
        """
        INSERT INTO task_response (workflow_id, task_id, sequence, payload, status, created_at)
        VALUES (:workflowId, :taskId, :sequence, CAST(:payload AS jsonb), :status, now())
        ON CONFLICT (workflow_id, task_id, sequence) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIgnoreDuplicate(
        @Param("workflowId") workflowId: Long,
        @Param("taskId") taskId: Long,
        @Param("sequence") sequence: Int,
        @Param("payload") payload: String?,
        @Param("status") status: String,
    )
}
