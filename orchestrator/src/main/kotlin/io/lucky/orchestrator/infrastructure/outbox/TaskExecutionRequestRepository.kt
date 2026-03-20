package io.lucky.orchestrator.infrastructure.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param

interface TaskExecutionRequestRepository : JpaRepository<TaskExecutionRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        "SELECT r FROM TaskExecutionRequest r WHERE r.published = false ORDER BY r.createdAt LIMIT :size",
    )
    fun findUnpublished(
        @Param("size") size: Int,
    ): List<TaskExecutionRequest>
}
