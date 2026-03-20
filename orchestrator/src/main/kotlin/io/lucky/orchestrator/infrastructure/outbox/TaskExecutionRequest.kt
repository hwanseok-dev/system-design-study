package io.lucky.orchestrator.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "task_execution_request")
class TaskExecutionRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "workflow_id", nullable = false)
    val workflowId: Long,
    @Column(name = "task_id", nullable = false)
    val taskId: Long,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String,
    @Column(nullable = false)
    var published: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
)
