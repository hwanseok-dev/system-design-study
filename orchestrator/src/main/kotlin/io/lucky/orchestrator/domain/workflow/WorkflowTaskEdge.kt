package io.lucky.orchestrator.domain.workflow

import io.lucky.orchestrator.domain.task.Task
import jakarta.persistence.Entity
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
    name = "workflow_task_edge",
    uniqueConstraints = [UniqueConstraint(columnNames = ["workflow_id", "parent_task_id", "child_task_id"])],
)
class WorkflowTaskEdge(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id", nullable = false)
    val parentTask: Task,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_task_id", nullable = false)
    val childTask: Task,
)
