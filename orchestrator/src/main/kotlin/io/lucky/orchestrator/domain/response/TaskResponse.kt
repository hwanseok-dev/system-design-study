package io.lucky.orchestrator.domain.response

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "task_response",
    uniqueConstraints = [UniqueConstraint(columnNames = ["workflow_id", "task_id", "sequence"])],
)
class TaskResponse(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "workflow_id", nullable = false)
    val workflowId: Long,
    @Column(name = "task_id", nullable = false)
    val taskId: Long,
    @Column(nullable = false)
    val sequence: Int,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val payload: String? = null,
    @Column(nullable = false)
    val status: String = "RECEIVED",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
