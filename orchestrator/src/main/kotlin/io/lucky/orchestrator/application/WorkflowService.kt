package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.api.WorkflowEdgeRequest
import io.lucky.orchestrator.api.WorkflowNodeRequest
import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.workflow.Workflow
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val taskRepository: TaskRepository,
) {
    @Transactional
    fun create(
        name: String,
        nodes: List<WorkflowNodeRequest>,
        edges: List<WorkflowEdgeRequest>,
    ): Workflow {
        val workflow = Workflow(name = name)

        val taskIds = nodes.map { it.taskId }
        val tasks = taskRepository.findAllById(taskIds).associateBy { it.id }

        nodes.forEach { node ->
            val task = tasks[node.taskId] ?: throw EntityNotFoundException("Task", node.taskId)
            workflow.addTask(task, task.expectedCount)
        }

        edges.forEach { edge ->
            val parent = tasks[edge.parentTaskId] ?: throw EntityNotFoundException("Task", edge.parentTaskId)
            val child = tasks[edge.childTaskId] ?: throw EntityNotFoundException("Task", edge.childTaskId)
            workflow.addEdge(parent, child)
        }

        val saved = workflowRepository.save(workflow)
        logger.info { "action=${LogAction.CREATE_WORKFLOW} workflowId=${saved.id} name=$name taskCount=${nodes.size}" }
        return saved
    }

    @Transactional
    fun start(workflowId: Long): List<WorkflowNode> {
        val workflow = getWorkflow(workflowId)

        val readyTasks = workflow.start()
        logger.info {
            "action=${LogAction.START_WORKFLOW} workflowId=$workflowId readyTasks=${readyTasks.map { it.task.name }}"
        }
        // TODO: ORCH-005 - save TaskExecutionRequest for each readyTask
        return readyTasks
    }

    @Transactional(readOnly = true)
    fun findById(workflowId: Long): Workflow = getWorkflow(workflowId)

    private fun getWorkflow(workflowId: Long): Workflow =
        workflowRepository
            .findByIdWithDetails(workflowId)
            .orElseThrow { EntityNotFoundException("Workflow", workflowId) }
}
