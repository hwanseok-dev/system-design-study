package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.domain.workflow.Workflow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface WorkflowRepository : JpaRepository<Workflow, Long> {
    @Query(
        """
        SELECT DISTINCT w FROM Workflow w
        LEFT JOIN FETCH w.nodes n
        LEFT JOIN FETCH n.task
        LEFT JOIN FETCH w.edges e
        LEFT JOIN FETCH e.parentTask
        LEFT JOIN FETCH e.childTask
        WHERE w.id = :id
        """,
    )
    fun findByIdWithDetails(id: Long): Optional<Workflow>
}
