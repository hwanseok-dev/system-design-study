package io.lucky.orchestrator.domain.workflow

import io.lucky.orchestrator.domain.BaseEntity
import io.lucky.orchestrator.domain.task.Task
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "workflow_node",
    uniqueConstraints = [UniqueConstraint(columnNames = ["workflow_id", "task_id"])],
)
class WorkflowNode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    val task: Task,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.CREATED,
    @Column(name = "expected_count", nullable = false)
    val expectedCount: Int,
    @Column(name = "completed_count", nullable = false)
    var completedCount: Int = 0,
) : BaseEntity() {
    fun markWaiting() {
        status = status.transitTo(TaskStatus.WAITING)
    }

    fun markRunning() {
        status = status.transitTo(TaskStatus.RUNNING)
    }

    fun markSucceeded() {
        status = status.transitTo(TaskStatus.SUCCEEDED)
    }

    fun markFailed() {
        status = status.transitTo(TaskStatus.FAILED)
    }

    fun isComplete(): Boolean = completedCount >= expectedCount
}
