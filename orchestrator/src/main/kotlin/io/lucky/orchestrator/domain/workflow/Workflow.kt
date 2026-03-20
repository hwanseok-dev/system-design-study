package io.lucky.orchestrator.domain.workflow

import io.lucky.orchestrator.domain.BaseEntity
import io.lucky.orchestrator.domain.task.Task
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "workflow")
class Workflow(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkflowStatus = WorkflowStatus.CREATED,
    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    val workflowTasks: MutableList<WorkflowTask> = mutableListOf(),
    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    val edges: MutableList<WorkflowTaskEdge> = mutableListOf(),
    @Version
    val version: Long = 0,
) : BaseEntity() {
    fun addTask(
        task: Task,
        expectedCount: Int,
    ): WorkflowTask {
        check(workflowTasks.none { it.task.id == task.id && task.id != 0L }) {
            "Task ${task.name} already exists in workflow"
        }
        check(workflowTasks.none { it.task.name == task.name }) {
            "Task ${task.name} already exists in workflow"
        }
        val workflowTask =
            WorkflowTask(
                workflow = this,
                task = task,
                expectedCount = expectedCount,
            )
        workflowTasks.add(workflowTask)
        return workflowTask
    }

    fun addEdge(
        parentTask: Task,
        childTask: Task,
    ) {
        val edge =
            WorkflowTaskEdge(
                workflow = this,
                parentTask = parentTask,
                childTask = childTask,
            )
        edges.add(edge)
        detectCycle()
    }

    fun start(): List<WorkflowTask> {
        status = status.transitTo(WorkflowStatus.RUNNING)
        workflowTasks.forEach { it.markWaiting() }
        val rootTasks = findRootTasks()
        rootTasks.forEach { it.markRunning() }
        return rootTasks
    }

    fun completeTask(taskId: Long): List<WorkflowTask> {
        val wt = findWorkflowTask(taskId)
        if (!wt.status.canTransitTo(TaskStatus.SUCCEEDED)) return emptyList()
        wt.markSucceeded()

        val newlyRunning = mutableListOf<WorkflowTask>()
        findChildTasks(taskId).forEach { child ->
            if (child.status == TaskStatus.WAITING && allParentsSucceeded(child)) {
                child.markRunning()
                newlyRunning.add(child)
            }
        }

        if (workflowTasks.all { it.status == TaskStatus.SUCCEEDED }) {
            status = status.transitTo(WorkflowStatus.SUCCEEDED)
        }

        return newlyRunning
    }

    fun failTask(taskId: Long) {
        val wt = findWorkflowTask(taskId)
        if (wt.status == TaskStatus.FAILED) return
        wt.markFailed()

        if (status.canTransitTo(WorkflowStatus.FAILED)) {
            status = status.transitTo(WorkflowStatus.FAILED)
        }

        propagateFailure(taskId)
    }

    fun calculateProgress(): Double {
        val totalExpected = workflowTasks.sumOf { it.expectedCount }
        if (totalExpected == 0) return 0.0
        val totalCompleted = workflowTasks.sumOf { it.completedCount }
        return totalCompleted.toDouble() / totalExpected
    }

    fun findWorkflowTask(taskId: Long): WorkflowTask = workflowTasks.first { it.task.id == taskId }

    private fun findRootTasks(): List<WorkflowTask> {
        val childTaskIds = edges.map { it.childTask.id }.toSet()
        return workflowTasks.filter { it.task.id !in childTaskIds }
    }

    private fun findChildTasks(taskId: Long): List<WorkflowTask> {
        val childIds = edges.filter { it.parentTask.id == taskId }.map { it.childTask.id }.toSet()
        return workflowTasks.filter { it.task.id in childIds }
    }

    private fun allParentsSucceeded(wt: WorkflowTask): Boolean {
        val parentIds = edges.filter { it.childTask.id == wt.task.id }.map { it.parentTask.id }
        return parentIds.all { parentId ->
            workflowTasks.first { it.task.id == parentId }.status == TaskStatus.SUCCEEDED
        }
    }

    private fun propagateFailure(taskId: Long) {
        findChildTasks(taskId).forEach { child ->
            if (child.status == TaskStatus.WAITING) {
                child.markFailed()
                propagateFailure(child.task.id)
            }
        }
    }

    private fun detectCycle() {
        val adjacency = mutableMapOf<Long, MutableList<Long>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.parentTask.id) { mutableListOf() }.add(edge.childTask.id)
        }

        val visited = mutableSetOf<Long>()
        val inStack = mutableSetOf<Long>()

        fun dfs(nodeId: Long): Boolean {
            visited.add(nodeId)
            inStack.add(nodeId)
            adjacency[nodeId]?.forEach { neighbor ->
                if (neighbor in inStack) return true
                if (neighbor !in visited && dfs(neighbor)) return true
            }
            inStack.remove(nodeId)
            return false
        }

        val allNodeIds = workflowTasks.map { it.task.id }.toSet()
        allNodeIds.forEach { nodeId ->
            if (nodeId !in visited && dfs(nodeId)) {
                edges.removeLast()
                throw IllegalStateException("Cycle detected in workflow DAG")
            }
        }
    }
}
