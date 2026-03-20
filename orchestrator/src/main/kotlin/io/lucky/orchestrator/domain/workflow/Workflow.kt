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
    val nodes: MutableSet<WorkflowNode> = mutableSetOf(),
    @OneToMany(mappedBy = "workflow", cascade = [CascadeType.ALL], orphanRemoval = true)
    val edges: MutableSet<WorkflowEdge> = mutableSetOf(),
    @Version
    val version: Long = 0,
) : BaseEntity() {
    fun addTask(
        task: Task,
        expectedCount: Int,
    ): WorkflowNode {
        check(nodes.none { it.task.id == task.id && task.id != 0L }) {
            "Task ${task.name} already exists in workflow"
        }
        check(nodes.none { it.task.name == task.name }) {
            "Task ${task.name} already exists in workflow"
        }
        val node =
            WorkflowNode(
                workflow = this,
                task = task,
                expectedCount = expectedCount,
            )
        nodes.add(node)
        return node
    }

    fun addEdge(
        parentTask: Task,
        childTask: Task,
    ) {
        val edge =
            WorkflowEdge(
                workflow = this,
                parentTask = parentTask,
                childTask = childTask,
            )
        edges.add(edge)
        try {
            detectCycle()
        } catch (e: IllegalStateException) {
            edges.remove(edge)
            throw e
        }
    }

    fun start(): List<WorkflowNode> {
        status = status.transitTo(WorkflowStatus.RUNNING)
        nodes.forEach { it.markWaiting() }
        val rootNodes = findRootNodes()
        rootNodes.forEach { it.markRunning() }
        return rootNodes
    }

    fun completeTask(taskId: Long): List<WorkflowNode> {
        val node = findNode(taskId)
        if (!node.status.canTransitTo(TaskStatus.SUCCEEDED)) return emptyList()
        node.markSucceeded()

        val newlyRunning = mutableListOf<WorkflowNode>()
        findChildNodes(taskId).forEach { child ->
            if (child.status == TaskStatus.WAITING && allParentsSucceeded(child)) {
                child.markRunning()
                newlyRunning.add(child)
            }
        }

        if (nodes.all { it.status == TaskStatus.SUCCEEDED }) {
            status = status.transitTo(WorkflowStatus.SUCCEEDED)
        }

        return newlyRunning
    }

    fun failTask(taskId: Long) {
        val node = findNode(taskId)
        if (node.status == TaskStatus.FAILED) return
        node.markFailed()

        if (status.canTransitTo(WorkflowStatus.FAILED)) {
            status = status.transitTo(WorkflowStatus.FAILED)
        }

        propagateFailure(taskId)
    }

    fun calculateProgress(): Double {
        val totalExpected = nodes.sumOf { it.expectedCount }
        if (totalExpected == 0) return 0.0
        val totalCompleted = nodes.sumOf { it.completedCount }
        return totalCompleted.toDouble() / totalExpected
    }

    fun findNode(taskId: Long): WorkflowNode = nodes.first { it.task.id == taskId }

    private fun findRootNodes(): List<WorkflowNode> {
        val childTaskIds = edges.map { it.childTask.id }.toSet()
        return nodes.filter { it.task.id !in childTaskIds }
    }

    private fun findChildNodes(taskId: Long): List<WorkflowNode> {
        val childIds = edges.filter { it.parentTask.id == taskId }.map { it.childTask.id }.toSet()
        return nodes.filter { it.task.id in childIds }
    }

    private fun allParentsSucceeded(node: WorkflowNode): Boolean {
        val parentIds = edges.filter { it.childTask.id == node.task.id }.map { it.parentTask.id }
        return parentIds.all { parentId ->
            nodes.first { it.task.id == parentId }.status == TaskStatus.SUCCEEDED
        }
    }

    private fun propagateFailure(taskId: Long) {
        findChildNodes(taskId).forEach { child ->
            if (child.status == TaskStatus.RUNNING) {
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

        val allNodeIds = nodes.map { it.task.id }.toSet()
        allNodeIds.forEach { nodeId ->
            if (nodeId !in visited && dfs(nodeId)) {
                throw IllegalStateException("Cycle detected in workflow DAG")
            }
        }
    }
}
